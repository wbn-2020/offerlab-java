$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    mvn -pl community-domain-post -am "-Dtest=CommunityTopicGuardTest,OperationCurationGuardTest,OperationCurationFeedbackGuardTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
    mvn -pl community-domain-analytics -am "-Dtest=CreatorCurationFeedbackGuardTest,CreatorCurationFeedbackServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
    mvn -pl community-domain-notification -am "-Dtest=CurationFeedbackNotificationGuardTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
} finally {
    Pop-Location
}
