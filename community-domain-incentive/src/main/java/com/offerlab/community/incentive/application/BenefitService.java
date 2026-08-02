package com.offerlab.community.incentive.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.incentive.api.IncentiveDtos.BenefitCatalogCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.BenefitDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.BenefitOrderCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.BenefitOrderDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.BenefitEntitlementDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.EntitlementConsumeCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.OrderActionCmd;
import com.offerlab.community.incentive.api.quota.BenefitCodes;
import com.offerlab.community.incentive.api.quota.EntitlementUsageDTO;
import com.offerlab.community.incentive.api.quota.EntitlementUsageStatus;
import com.offerlab.community.incentive.domain.IncentiveTypes;
import com.offerlab.community.incentive.infrastructure.IncentiveMapper;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.BenefitOrderPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.BenefitPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.BenefitEntitlementPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.BenefitEntitlementUsagePO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.LedgerPO;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.AdminPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class BenefitService {
    private static final Set<String> ENABLED_CONSUMABLE_BENEFITS =
            Set.of(BenefitCodes.AI_ASSIST_QUOTA);
    private static final Set<String> PROHIBITED_BENEFIT_TERMS = Set.of(
            "充值", "提现", "转账", "现金", "人民币", "抽奖", "赌博", "认证", "专家", "曝光", "流量", "审核权",
            "recharge", "withdraw", "transfer", "cash", "lottery", "gambling", "certification",
            "expert", "exposure", "traffic boost", "moderation", "review priority");
    private final IncentiveMapper mapper;
    private final AccountLedgerService accountLedgerService;
    private final SnowflakeIdGenerator idGenerator;
    private final AdminPermissionService adminPermissionService;
    private final AdminAuditService adminAuditService;
    private final ObjectMapper objectMapper;
    private final EntitlementConsumerRegistry entitlementConsumerRegistry;

    public PageResult<BenefitDTO> catalog(Integer page, Integer size) {
        int safePage = safePage(page);
        int safeSize = IncentiveTypes.safeLimit(size);
        List<BenefitDTO> items = mapper.selectBenefits((safePage - 1) * safeSize, safeSize).stream()
                .filter(item -> entitlementConsumerRegistry.isRuntimeAvailable(item.getBenefitCode()))
                .map(this::toBenefitDto)
                .toList();
        return page(items, items.size(), safePage, safeSize);
    }

    public PageResult<BenefitOrderDTO> orders(Long userId, Integer page, Integer size) {
        requireUser(userId);
        int safePage = safePage(page);
        int safeSize = IncentiveTypes.safeLimit(size);
        List<BenefitOrderDTO> items = mapper.selectUserOrders(userId, (safePage - 1) * safeSize, safeSize).stream()
                .map(this::toOrderDto)
                .toList();
        return page(items, mapper.countUserOrders(userId), safePage, safeSize);
    }

    public BenefitOrderDTO orderByIdempotency(Long userId, String idempotencyKey) {
        requireUser(userId);
        String key = IncentiveTypes.requireText(idempotencyKey, 96, "idempotencyKey");
        BenefitOrderPO order = mapper.selectOrderByIdempotency(userId, key);
        if (order == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return toOrderDto(order);
    }

    public PageResult<BenefitEntitlementDTO> entitlements(Long userId, Integer page, Integer size) {
        requireUser(userId);
        int safePage = safePage(page);
        int safeSize = IncentiveTypes.safeLimit(size);
        List<BenefitEntitlementDTO> items = mapper.selectUserEntitlements(
                userId, (safePage - 1) * safeSize, safeSize).stream().map(this::toEntitlementDto).toList();
        return page(items, mapper.countUserEntitlements(userId), safePage, safeSize);
    }

    public PageResult<EntitlementUsageDTO> entitlementUsages(Long userId, Integer page, Integer size) {
        requireUser(userId);
        int safePage = safePage(page);
        int safeSize = IncentiveTypes.safeLimit(size);
        List<EntitlementUsageDTO> items = mapper.selectUserEntitlementUsages(
                        userId, (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(this::toEntitlementUsageDto)
                .toList();
        return page(items, mapper.countUserEntitlementUsages(userId), safePage, safeSize);
    }

    public BenefitEntitlementDTO consumeEntitlement(Long entitlementId, EntitlementConsumeCmd cmd, Long userId) {
        requireUser(userId);
        throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                "ENTITLEMENT_CONSUME_REQUIRES_BUSINESS_CONTEXT");
    }

    public PageResult<BenefitOrderDTO> adminOrders(String status, Integer page, Integer size, Long operatorUid) {
        requireAdmin(operatorUid);
        String normalizedStatus = status == null ? null : requireOrderStatus(status);
        int safePage = safePage(page);
        int safeSize = IncentiveTypes.safeLimit(size);
        List<BenefitOrderDTO> items = mapper.selectAdminOrders(normalizedStatus, (safePage - 1) * safeSize, safeSize)
                .stream().map(this::toOrderDto).toList();
        return page(items, mapper.countAdminOrders(normalizedStatus), safePage, safeSize);
    }

    public PageResult<BenefitDTO> adminCatalog(Integer page, Integer size, Long operatorUid) {
        requireAdmin(operatorUid);
        int safePage = safePage(page);
        int safeSize = IncentiveTypes.safeLimit(size);
        List<BenefitDTO> items = mapper.selectAllBenefits((safePage - 1) * safeSize, safeSize).stream()
                .map(this::toBenefitDto)
                .toList();
        return page(items, mapper.countAllBenefits(), safePage, safeSize);
    }

    @Transactional
    public BenefitDTO upsertCatalog(BenefitCatalogCmd cmd, Long operatorUid) {
        requireAdmin(operatorUid);
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String reason = IncentiveTypes.requireReason(cmd.getReason());
        String category = IncentiveTypes.requireText(cmd.getCategory(), 32, "category").toUpperCase(Locale.ROOT);
        String deliveryType = IncentiveTypes.requireText(cmd.getDeliveryType(), 32, "deliveryType")
                .toUpperCase(Locale.ROOT);
        requireAllowedBenefitShape(category, deliveryType);
        long pointCost = IncentiveTypes.requirePositive(cmd.getPointCost(), "pointCost");
        if (cmd.getTotalStock() != null && cmd.getTotalStock() < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        BenefitPO po = new BenefitPO();
        po.setId(idGenerator.nextId());
        po.setBenefitCode(IncentiveTypes.requireText(cmd.getBenefitCode(), 64, "benefitCode").toUpperCase(Locale.ROOT));
        po.setBenefitName(IncentiveTypes.requireText(cmd.getName(), 128, "name"));
        po.setDescription(IncentiveTypes.clean(cmd.getDescription(), 1000));
        po.setCategory(category);
        po.setDeliveryType(deliveryType);
        po.setPointCost(pointCost);
        validateBenefitText(po.getBenefitCode(), po.getBenefitName(), po.getDescription(), po.getDeliveryType());
        BenefitPO existing = mapper.lockBenefitByCode(po.getBenefitCode());
        Integer requestedStock = cmd.getTotalStock();
        long orderCount = existing == null ? 0 : mapper.countBenefitOrders(existing.getId());
        if (existing != null && orderCount > 0) {
            if (!Objects.equals(existing.getCategory(), category)
                    || !Objects.equals(existing.getDeliveryType(), deliveryType)
                    || !Objects.equals(existing.getPointCost(), pointCost)) {
                throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                        "category, deliveryType and pointCost cannot change after orders exist");
            }
            if (!Objects.equals(existing.getTotalStock(), requestedStock)
                    && (existing.getTotalStock() == null || requestedStock == null)) {
                throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                        "stock mode cannot change after orders exist");
            }
        }
        int sold = existing == null || existing.getTotalStock() == null || existing.getAvailableStock() == null
                ? 0
                : Math.max(0, existing.getTotalStock() - existing.getAvailableStock());
        if (requestedStock != null && requestedStock < sold) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "totalStock cannot be lower than already sold quantity");
        }
        po.setTotalStock(requestedStock);
        po.setAvailableStock(requestedStock == null ? null : requestedStock - sold);
        if (existing != null) {
            po.setId(existing.getId());
            po.setCreatedBy(existing.getCreatedBy());
        } else {
            po.setCreatedBy(operatorUid);
        }
        po.setEnabled(Boolean.TRUE.equals(cmd.getEnabled())
                && ENABLED_CONSUMABLE_BENEFITS.contains(po.getBenefitCode())
                && entitlementConsumerRegistry.hasInstalledConsumer(po.getBenefitCode()) ? 1 : 0);
        po.setUpdatedBy(operatorUid);
        po.setActionReason(reason);
        mapper.upsertBenefit(po);
        BenefitPO saved = findBenefitByCode(po.getBenefitCode());
        adminAuditService.recordRequired(operatorUid, "VIRTUAL_BENEFIT_CATALOG_UPSERT",
                "VIRTUAL_BENEFIT", saved.getId(), null, saved, reason);
        return toBenefitDto(saved);
    }

    @Transactional
    public BenefitOrderDTO placeOrder(BenefitOrderCmd cmd, Long userId) {
        requireUser(userId);
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String idempotencyKey = IncentiveTypes.requireText(cmd.getIdempotencyKey(), 96, "idempotencyKey");
        int quantity = cmd.getQuantity() == null ? 1 : cmd.getQuantity();
        if (quantity <= 0 || quantity > 10) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String requestFingerprint = sha256(userId + "|" + cmd.getBenefitId() + "|" + quantity);
        BenefitOrderPO existing = mapper.selectOrderByIdempotency(userId, idempotencyKey);
        if (existing != null) {
            requireEquivalentOrder(existing, requestFingerprint);
            return toOrderDto(existing);
        }
        BenefitPO benefit = mapper.lockBenefit(cmd.getBenefitId());
        if (benefit == null || benefit.getEnabled() == null || benefit.getEnabled() != 1) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!ENABLED_CONSUMABLE_BENEFITS.contains(benefit.getBenefitCode())) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!entitlementConsumerRegistry.isRuntimeAvailable(benefit.getBenefitCode())) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "AI_ASSIST_DISABLED");
        }
        requireAllowedBenefitShape(benefit.getCategory(), benefit.getDeliveryType());
        long totalCost;
        try {
            totalCost = Math.multiplyExact(benefit.getPointCost(), quantity);
        } catch (ArithmeticException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "order point cost is too large");
        }
        if (benefit.getTotalStock() != null && mapper.reserveBenefitStock(benefit.getId(), quantity) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "benefit stock is unavailable");
        }
        BenefitOrderPO order = new BenefitOrderPO();
        order.setId(idGenerator.nextId());
        order.setOrderNo("VB" + order.getId());
        order.setIdempotencyKey(idempotencyKey);
        order.setRequestFingerprint(requestFingerprint);
        order.setUserId(userId);
        order.setBenefitId(benefit.getId());
        order.setBenefitCode(benefit.getBenefitCode());
        order.setBenefitName(benefit.getBenefitName());
        order.setQuantity(quantity);
        order.setUnitPointCost(benefit.getPointCost());
        order.setTotalPointCost(totalCost);
        order.setOrderStatus("CREATED");
        try {
            mapper.insertBenefitOrder(order);
        } catch (DuplicateKeyException e) {
            if (benefit.getTotalStock() != null) {
                mapper.restoreBenefitStock(benefit.getId(), quantity);
            }
            BenefitOrderPO concurrent = mapper.lockOrderByIdempotency(userId, idempotencyKey);
            if (concurrent != null) {
                requireEquivalentOrder(concurrent, requestFingerprint);
                return toOrderDto(concurrent);
            }
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        history(order.getId(), null, "CREATED", userId, "Order created");
        LedgerPO reserve = accountLedgerService.spendPoints(userId, totalCost,
                "BENEFIT:RESERVE:" + order.getId(), "BENEFIT_ORDER", String.valueOf(order.getId()),
                "Reserve points for virtual benefit " + benefit.getBenefitCode());
        if (mapper.transitionBenefitOrder(order.getId(), "CREATED", "RESERVED", reserve.getId(),
                null, null, userId, "Points and stock reserved") != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        history(order.getId(), "CREATED", "RESERVED", userId, "Points and stock reserved");
        BenefitEntitlementPO entitlement = buildEntitlement(order, "AUTO:" + order.getOrderNo(), userId,
                "Automatic account entitlement delivery");
        if (mapper.insertBenefitEntitlement(entitlement) != 1) {
            throw new BizException(ErrorCode.DATABASE_ERROR.getCode(), "automatic entitlement delivery failed");
        }
        if (mapper.transitionBenefitOrder(order.getId(), "RESERVED", "DELIVERED", null, null,
                entitlement.getEntitlementKey(), userId, "Automatic account entitlement delivery") != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        history(order.getId(), "RESERVED", "DELIVERED", userId, "Automatic account entitlement delivery");
        return toOrderDto(mapper.lockBenefitOrder(order.getId()));
    }

    @Transactional
    public BenefitOrderDTO cancelOrder(Long orderId, OrderActionCmd cmd, Long userId) {
        requireUser(userId);
        String reason = IncentiveTypes.requireReason(cmd == null ? null : cmd.getReason());
        BenefitOrderPO order = requireOrder(orderId);
        if (!userId.equals(order.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return cancelReserved(order, reason, userId, false);
    }

    @Transactional
    public BenefitOrderDTO deliver(Long orderId, OrderActionCmd cmd, Long operatorUid) {
        requireAdmin(operatorUid);
        String reason = IncentiveTypes.requireReason(cmd == null ? null : cmd.getReason());
        String deliveryReference = IncentiveTypes.clean(cmd.getDeliveryReference(), 256);
        BenefitOrderPO order = requireOrder(orderId);
        if (!"RESERVED".equals(order.getOrderStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        BenefitOrderDTO before = toOrderDto(order);
        BenefitEntitlementPO entitlement = buildEntitlement(order, deliveryReference, operatorUid, reason);
        mapper.insertBenefitEntitlement(entitlement);
        if (mapper.transitionBenefitOrder(orderId, "RESERVED", "DELIVERED", null, null,
                deliveryReference, operatorUid, reason) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        history(orderId, "RESERVED", "DELIVERED", operatorUid, reason);
        BenefitOrderDTO after = toOrderDto(mapper.lockBenefitOrder(orderId));
        audit(operatorUid, "VIRTUAL_BENEFIT_ORDER_DELIVER", orderId, before, after, reason);
        return after;
    }

    @Transactional
    public BenefitOrderDTO adminCancel(Long orderId, OrderActionCmd cmd, Long operatorUid) {
        requireAdmin(operatorUid);
        String reason = IncentiveTypes.requireReason(cmd == null ? null : cmd.getReason());
        return cancelReserved(requireOrder(orderId), reason, operatorUid, true);
    }

    @Transactional
    public BenefitOrderDTO refund(Long orderId, OrderActionCmd cmd, Long operatorUid) {
        requireAdmin(operatorUid);
        String reason = IncentiveTypes.requireReason(cmd == null ? null : cmd.getReason());
        BenefitOrderPO order = requireOrder(orderId);
        if (!"DELIVERED".equals(order.getOrderStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        BenefitOrderDTO before = toOrderDto(order);
        BenefitEntitlementPO entitlement = mapper.lockEntitlementByOrder(orderId);
        if (entitlement == null || mapper.countRefundBlockingEntitlementUsage(entitlement.getId()) > 0
                || !Objects.equals(entitlement.getQuantityRemaining(), entitlement.getQuantityTotal())
                || entitlement.getReversible() == null || entitlement.getReversible() != 1
                || mapper.revokeEntitlementByOrder(orderId, operatorUid, reason) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                    "delivered entitlement is not unused and reversibly refundable");
        }
        BenefitPO benefit = mapper.lockBenefit(order.getBenefitId());
        LedgerPO refund = accountLedgerService.refundPoints(order.getUserId(), order.getTotalPointCost(),
                "BENEFIT:REFUND:" + orderId, "BENEFIT_ORDER", String.valueOf(orderId), reason, operatorUid);
        if (mapper.transitionBenefitOrder(orderId, "DELIVERED", "REFUNDED", null, refund.getId(),
                null, operatorUid, reason) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        if (benefit != null && benefit.getTotalStock() != null
                && restoresStockAfterDeliveredRefund(benefit.getDeliveryType())) {
            mapper.restoreBenefitStock(order.getBenefitId(), order.getQuantity());
        }
        history(orderId, "DELIVERED", "REFUNDED", operatorUid, reason);
        BenefitOrderDTO after = toOrderDto(mapper.lockBenefitOrder(orderId));
        audit(operatorUid, "VIRTUAL_BENEFIT_ORDER_REFUND", orderId, before, after, reason);
        return after;
    }

    private BenefitOrderDTO cancelReserved(BenefitOrderPO order, String reason, Long operatorUid, boolean admin) {
        if (!"RESERVED".equals(order.getOrderStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        BenefitOrderDTO before = toOrderDto(order);
        BenefitPO benefit = mapper.lockBenefit(order.getBenefitId());
        LedgerPO refund = accountLedgerService.refundPoints(order.getUserId(), order.getTotalPointCost(),
                "BENEFIT:CANCEL:" + order.getId(), "BENEFIT_ORDER", String.valueOf(order.getId()), reason, operatorUid);
        if (benefit != null && benefit.getTotalStock() != null) {
            mapper.restoreBenefitStock(order.getBenefitId(), order.getQuantity());
        }
        if (mapper.transitionBenefitOrder(order.getId(), "RESERVED", "CANCELLED", null, refund.getId(),
                null, operatorUid, reason) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        history(order.getId(), "RESERVED", "CANCELLED", operatorUid, reason);
        BenefitOrderDTO after = toOrderDto(mapper.lockBenefitOrder(order.getId()));
        if (admin) {
            audit(operatorUid, "VIRTUAL_BENEFIT_ORDER_CANCEL", order.getId(), before, after, reason);
        }
        return after;
    }

    private BenefitPO findBenefitByCode(String code) {
        BenefitPO po = mapper.selectBenefitByCode(code);
        if (po == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return po;
    }

    private BenefitOrderPO requireOrder(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        BenefitOrderPO order = mapper.lockBenefitOrder(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return order;
    }

    private void requireAllowedBenefitShape(String category, String deliveryType) {
        String normalized = IncentiveTypes.upper(category);
        String normalizedDelivery = IncentiveTypes.upper(deliveryType);
        if (!IncentiveTypes.BENEFIT_CATEGORIES.contains(normalized)
                || !IncentiveTypes.BENEFIT_DELIVERY_TYPES.contains(normalizedDelivery)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "benefit category and deliveryType must use the low-risk allowlist");
        }
    }

    private boolean restoresStockAfterDeliveredRefund(String deliveryType) {
        String normalized = IncentiveTypes.upper(deliveryType);
        return "REVERSIBLE".equals(normalized) || "ACCOUNT_ENTITLEMENT".equals(normalized);
    }

    private void validateBenefitText(String... values) {
        for (String value : values) {
            String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
            if (PROHIBITED_BENEFIT_TERMS.stream().anyMatch(normalized::contains)) {
                throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                        "benefit cannot sell money, chance, certification, exposure or authority");
            }
        }
    }

    private void history(Long orderId, String from, String to, Long operatorUid, String reason) {
        mapper.insertOrderHistory(idGenerator.nextId(), orderId, from, to, operatorUid, reason);
    }

    private void audit(Long operatorUid, String action, Long orderId, Object before, Object after, String reason) {
        adminAuditService.recordRequired(operatorUid, action, "VIRTUAL_BENEFIT_ORDER", orderId,
                before, after, reason);
    }

    private BenefitEntitlementPO buildEntitlement(BenefitOrderPO order, String deliveryReference,
                                                   Long operatorUid, String reason) {
        BenefitPO benefit = mapper.selectBenefit(order.getBenefitId());
        if (benefit == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        requireAllowedBenefitShape(benefit.getCategory(), benefit.getDeliveryType());
        if (!ENABLED_CONSUMABLE_BENEFITS.contains(benefit.getBenefitCode())) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                    "decorative entitlement delivery is disabled until a real consumer exists");
        }
        long quantity = switch (benefit.getBenefitCode()) {
            case "AI_ASSIST_QUOTA" -> Math.multiplyExact(order.getQuantity().longValue(), 10L);
            default -> order.getQuantity().longValue();
        };
        String type = "CONSUMABLE_QUOTA";
        BenefitEntitlementPO po = new BenefitEntitlementPO();
        po.setId(idGenerator.nextId());
        po.setOrderId(order.getId());
        po.setUserId(order.getUserId());
        po.setBenefitCode(benefit.getBenefitCode());
        po.setEntitlementType(type);
        po.setEntitlementKey(benefit.getBenefitCode());
        po.setQuantityTotal(quantity);
        po.setQuantityRemaining(quantity);
        po.setEntitlementStatus("ACTIVE");
        po.setReversible("MANUAL".equals(benefit.getDeliveryType()) ? 0 : 1);
        po.setPayloadJson(entitlementPayload(order, deliveryReference));
        po.setGrantedBy(operatorUid);
        po.setGrantReason(reason);
        return po;
    }

    private String entitlementPayload(BenefitOrderPO order, String deliveryReference) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "orderId", order.getId(),
                    "benefitCode", order.getBenefitCode(),
                    "deliveryReference", deliveryReference == null ? "" : deliveryReference));
        } catch (Exception e) {
            throw new IllegalStateException("benefit entitlement payload serialization failed", e);
        }
    }

    private BenefitDTO toBenefitDto(BenefitPO po) {
        return BenefitDTO.builder()
                .id(po.getId()).benefitCode(po.getBenefitCode()).name(po.getBenefitName())
                .description(po.getDescription()).category(po.getCategory()).deliveryType(po.getDeliveryType())
                .pointCost(po.getPointCost()).totalStock(po.getTotalStock()).availableStock(po.getAvailableStock())
                .enabled(po.getEnabled() != null && po.getEnabled() == 1).updateTime(po.getUpdateTime())
                .build();
    }

    private BenefitOrderDTO toOrderDto(BenefitOrderPO po) {
        return BenefitOrderDTO.builder()
                .id(po.getId()).orderNo(po.getOrderNo()).userId(po.getUserId()).benefitId(po.getBenefitId())
                .benefitCode(po.getBenefitCode()).benefitName(po.getBenefitName()).quantity(po.getQuantity())
                .unitPointCost(po.getUnitPointCost()).totalPointCost(po.getTotalPointCost())
                .status(po.getOrderStatus()).deliveryReference(po.getDeliveryReference())
                .actionReason(po.getActionReason()).operatorUid(po.getOperatorUid())
                .createTime(po.getCreateTime()).updateTime(po.getUpdateTime())
                .build();
    }

    private BenefitEntitlementDTO toEntitlementDto(BenefitEntitlementPO po) {
        return BenefitEntitlementDTO.builder()
                .id(po.getId()).orderId(po.getOrderId()).userId(po.getUserId())
                .benefitCode(po.getBenefitCode()).entitlementType(po.getEntitlementType())
                .entitlementKey(po.getEntitlementKey()).quantityTotal(po.getQuantityTotal())
                .quantityRemaining(po.getQuantityRemaining()).status(po.getEntitlementStatus())
                .reversible(po.getReversible() != null && po.getReversible() == 1)
                .payloadJson(po.getPayloadJson()).grantedAt(po.getGrantedAt())
                .revokedAt(po.getRevokedAt()).updateTime(po.getUpdateTime()).build();
    }

    private EntitlementUsageDTO toEntitlementUsageDto(BenefitEntitlementUsagePO po) {
        return new EntitlementUsageDTO(
                po.getId(),
                po.getEntitlementId(),
                po.getUserId(),
                po.getBenefitCode(),
                po.getConsumerCode(),
                EntitlementUsageStatus.valueOf(po.getUsageStatus()),
                po.getAmount(),
                po.getIdempotencyKey(),
                po.getRequestFingerprint(),
                po.getFailureCode(),
                po.getExpiresAt(),
                po.getConfirmedAt(),
                po.getReleasedAt()
        );
    }

    private void requireAdmin(Long uid) {
        adminPermissionService.requireAdmin(uid);
    }

    private static void requireUser(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static String requireOrderStatus(String status) {
        String normalized = IncentiveTypes.upper(status);
        if (!IncentiveTypes.ORDER_STATUSES.contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static int safePage(Integer page) {
        return page == null || page <= 0 ? 1 : Math.min(page, 1000);
    }

    private static void requireEquivalentOrder(BenefitOrderPO order, String fingerprint) {
        if (order == null || !Objects.equals(order.getRequestFingerprint(), fingerprint)) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                    "benefit order idempotency key was reused for another request");
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static <T> PageResult<T> page(List<T> items, long total, int page, int size) {
        boolean hasMore = (long) page * size < total;
        return PageResult.<T>builder().items(items).total(total).hasMore(hasMore)
                .nextCursor(hasMore ? String.valueOf(page + 1) : null).build();
    }
}
