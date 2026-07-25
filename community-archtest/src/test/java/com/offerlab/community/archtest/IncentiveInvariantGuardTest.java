package com.offerlab.community.archtest;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural guard for the product's hardest incentive invariants:
 *  - reputation can never be spent/transferred/withdrawn (only granted / reversed / governance-adjusted);
 *  - there is no real money (no currency-bearing ledger/benefit fields, no payment SDK dependency);
 *  - the only account type that participates in a SPEND entry is POINT;
 *  - points can never be cashed out.
 *
 * These invariants were previously enforced only by hardcoded string literals and runtime text
 * blacklists. A single accidental edit (passing "REPUTATION" into a spend, or adding a currency
 * field to the ledger) could silently break them. This guard pins them down so such a regression
 * turns the build red.
 */
class IncentiveInvariantGuardTest {

    private static final String INCENTIVE_MAIN =
            "../community-domain-incentive/src/main/java/com/offerlab/community/incentive";

    // ------------------------------------------------------------------
    // Invariant 1 + 3: reputation is never spent; SPEND is POINT-only.
    // ------------------------------------------------------------------
    @Test
    void reputationIsNeverSpentAndSpendIsPointOnly() throws Exception {
        String ledger = read(INCENTIVE_MAIN + "/application/AccountLedgerService.java");

        // The public consumption entry point exists and is POINT-scoped by literal, not a parameter.
        assertContains(ledger,
                "public LedgerPO spendPoints(Long userId, long amount, String idempotencyKey,",
                "point consumption must go through the explicit spendPoints entry point");
        assertContains(ledger,
                "append(userId, \"POINT\", IncentiveTypes.GLOBAL_DOMAIN, \"SPEND\",",
                "spendPoints must append a SPEND entry against the POINT account by literal, not a variable account type");

        // Every SPEND append in the ledger must use the "POINT" literal as its account type.
        // We scan every append(...) call whose entryType argument is "SPEND" and assert the
        // account-type argument is the "POINT" literal.
        for (SpendAppend spend : findSpendAppends(ledger)) {
            assertTrue(spend.accountTypeArg.equals("\"POINT\""),
                    "SPEND ledger entry must use the POINT account literal, found account type argument: "
                            + spend.accountTypeArg + " near: " + spend.snippet);
        }

        // No SPEND-style ledger call may pass REPUTATION. This is a defensive text assertion:
        // "REPUTATION" must never appear on the same append/entry line as "SPEND".
        for (String line : ledger.split("\n")) {
            if (line.contains("\"SPEND\"") && line.contains("REPUTATION")) {
                throw new AssertionError("a SPEND ledger entry must never reference REPUTATION: " + line.trim());
            }
        }

        // The reputation account type only enters the ledger through non-consumptive paths.
        // In the reward pipeline REPUTATION is enqueued as a reward rule account type; it must not
        // appear next to consumption entry types.
        String rewardListener = read(INCENTIVE_MAIN + "/application/TrustedContributionRewardListener.java");
        for (String line : rewardListener.split("\n")) {
            if (line.contains("REPUTATION")
                    && (line.contains("\"SPEND\"") || line.contains("\"WITHDRAW\"") || line.contains("\"TRANSFER\""))) {
                throw new AssertionError("reputation must not be attached to a consumption/transfer entry: " + line.trim());
            }
        }
    }

    // ------------------------------------------------------------------
    // Invariant 4: points can never be cashed out / withdrawn / transferred.
    // ------------------------------------------------------------------
    @Test
    void thereIsNoCashOutOrTransferSurface() throws Exception {
        // No method in the whole incentive domain may expose a cash-out / withdraw / transfer API.
        List<Path> javaFiles;
        try (var stream = Files.walk(Path.of(INCENTIVE_MAIN))) {
            javaFiles = stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();
        }
        assertTrue(!javaFiles.isEmpty(), "incentive domain sources must be discoverable for the invariant scan");

        // A "method that can move value out" is a public/private method whose name starts with one
        // of these forbidden verbs. We match on a Java method-declaration shape to avoid flagging
        // unrelated identifiers (e.g. a "transferReason" field or a comment).
        Pattern forbiddenMethod = Pattern.compile(
                "\\b(public|private|protected)\\s+[\\w<>,\\[\\]\\s]+\\s"
                        + "(cashOut|withdraw|withdrawPoints|redeemToCash|transferPoints|transferBalance|payout)\\s*\\(");
        for (Path file : javaFiles) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = forbiddenMethod.matcher(source);
            if (matcher.find()) {
                throw new AssertionError("incentive domain must not expose a cash-out/withdraw/transfer method: "
                        + matcher.group(2) + " in " + file);
            }
        }
    }

    // ------------------------------------------------------------------
    // Invariant 2: there is no real money.
    // ------------------------------------------------------------------
    @Test
    void ledgerAndBenefitFieldsCarryNoRealMoneySemantics() throws Exception {
        String persistence = read(INCENTIVE_MAIN + "/infrastructure/IncentivePersistence.java");
        String dtos = read(INCENTIVE_MAIN + "/api/IncentiveDtos.java");

        // Currency-bearing field names would signal a drift toward real money. Match on field
        // declarations (private <Type> <name>;) so that governance blacklist strings that contain
        // "cash"/"现金" as forbidden BENEFIT terms are not themselves flagged.
        Pattern fieldDecl = Pattern.compile("private\\s+[\\w<>,\\[\\]\\.\\s]+\\s(\\w+)\\s*;");
        for (String source : List.of(persistence, dtos)) {
            Matcher matcher = fieldDecl.matcher(source);
            while (matcher.find()) {
                String field = matcher.group(1).toLowerCase();
                for (String forbidden : List.of("currency", "cash", "money", "price", "amountcny", "rmb", "cny", "usd", "fiat")) {
                    assertFalse(field.contains(forbidden),
                            "incentive ledger/benefit fields must not carry real-money semantics, found field: " + matcher.group(1));
                }
            }
        }

        // No payment SDK may be pulled into the incentive module.
        String incentivePom = read("../community-domain-incentive/pom.xml");
        for (String paymentArtifact : List.of(
                "alipay", "wechatpay", "wxpay", "stripe", "paypal", "unionpay",
                "payment-sdk", "pay-sdk", "braintree", "adyen")) {
            assertFalse(incentivePom.toLowerCase().contains(paymentArtifact),
                    "incentive module must not depend on a payment SDK: " + paymentArtifact);
        }
    }

    // ------------------------------------------------------------------
    // Invariant guard integrity: the account-type universe stays exactly {REPUTATION, POINT}
    // and reputation stays global-forbidden-to-spend by construction.
    // ------------------------------------------------------------------
    @Test
    void accountTypeUniverseStaysConstrained() throws Exception {
        String types = read(INCENTIVE_MAIN + "/domain/IncentiveTypes.java");
        assertContains(types,
                "ACCOUNT_TYPES = Set.of(\"REPUTATION\", \"POINT\")",
                "the account-type universe must remain exactly {REPUTATION, POINT} - new spendable types need explicit review");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** A parsed append(...) invocation that carries the "SPEND" entry type. */
    private record SpendAppend(String accountTypeArg, String snippet) {
    }

    private static List<SpendAppend> findSpendAppends(String source) {
        // Match: append( <account-type-arg> , <domain-arg> , "SPEND" ,
        // The account-type argument is the first argument after append(userId, .
        Pattern pattern = Pattern.compile(
                "append\\(\\s*[\\w\\.]+\\s*,\\s*([^,]+?)\\s*,\\s*[^,]+?\\s*,\\s*\"SPEND\"");
        Matcher matcher = pattern.matcher(source);
        java.util.ArrayList<SpendAppend> result = new java.util.ArrayList<>();
        while (matcher.find()) {
            result.add(new SpendAppend(matcher.group(1).trim(), matcher.group().replace("\n", " ")));
        }
        assertTrue(!result.isEmpty(),
                "expected at least one SPEND append in the ledger service - the guard must not silently pass on a parse miss");
        return result;
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static void assertContains(String source, String needle, String message) {
        assertTrue(source.contains(needle), message);
    }
}
