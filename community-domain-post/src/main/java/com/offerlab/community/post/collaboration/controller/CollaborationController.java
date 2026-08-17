package com.offerlab.community.post.collaboration.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.post.collaboration.api.CollaborationModels.*;
import com.offerlab.community.post.collaboration.application.CollaborationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/collaboration")
@RequiredArgsConstructor
@Validated
public class CollaborationController {

    private final CollaborationService service;

    @PublicApi
    @GetMapping("/needs")
    @RateLimit(key = "'public:collaboration:needs:' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<NeedDTO>> listNeeds(
            @RequestParam(required = false) @Min(1) @Max(5) Integer domain,
            @RequestParam(required = false) @Size(max = 24) String status,
            @RequestParam(defaultValue = "0") @Min(0) long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            HttpServletRequest request) {
        return Result.ok(service.listNeeds(domain, status, UserContext.get(), cursor, size));
    }

    @GetMapping("/needs/mine")
    @RateLimit(key = "'collaboration:needs:mine:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<NeedDTO>> listMyClaimedNeeds(
            @RequestParam(required = false) @Size(max = 24) String status,
            @RequestParam(defaultValue = "0") @Min(0) long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(service.listMyClaimedNeeds(UserContext.require(), status, cursor, size));
    }

    @GetMapping("/needs/mine/created")
    @RateLimit(key = "'collaboration:needs:mine:created:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<NeedDTO>> listMyCreatedNeeds(
            @RequestParam(required = false) @Size(max = 24) String status,
            @RequestParam(defaultValue = "0") @Min(0) long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(service.listMyCreatedNeeds(UserContext.require(), status, cursor, size));
    }

    @GetMapping("/needs/mine/followed")
    @RateLimit(key = "'collaboration:needs:mine:followed:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<NeedDTO>> listMyFollowedNeeds(
            @RequestParam(required = false) @Size(max = 24) String status,
            @RequestParam(defaultValue = "0") @Min(0) long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(service.listMyFollowedNeeds(UserContext.require(), status, cursor, size));
    }

    @GetMapping("/needs/review-queue")
    @RateLimit(key = "'collaboration:needs:review-list:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<NeedDTO>> listNeedReviewQueue(
            @RequestParam(required = false) @Min(1) @Max(5) Integer domain,
            @RequestParam(defaultValue = "0") @Min(0) long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(service.listNeedReviewQueue(domain, UserContext.require(), cursor, size));
    }

    @PublicApi
    @GetMapping("/needs/{needId}")
    @RateLimit(key = "'public:collaboration:need:' + #needId + ':' + #request.remoteAddr", rate = 180, per = 60, failOpen = false)
    public Result<NeedDTO> getNeed(@PathVariable @Positive Long needId, HttpServletRequest request) {
        return Result.ok(service.getNeed(needId, UserContext.get()));
    }

    @PublicApi
    @GetMapping("/needs/{needId}/events")
    @RateLimit(key = "'public:collaboration:need-events:' + #needId + ':' + #request.remoteAddr",
            rate = 120, per = 60, failOpen = false)
    public Result<NeedEventTimelineDTO> listNeedEvents(
            @PathVariable @Positive Long needId,
            @RequestParam(defaultValue = "0") @Min(0) long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            HttpServletRequest request) {
        return Result.ok(service.listNeedEvents(needId, UserContext.get(), cursor, size));
    }

    @PostMapping("/needs")
    @RateLimit(key = "'collaboration:need:create:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<NeedDTO> createNeed(@Valid @RequestBody NeedCreateCmd cmd) {
        return Result.ok(service.createNeed(cmd, UserContext.require()));
    }

    @PostMapping("/needs/{needId}/follow")
    @RateLimit(key = "'collaboration:need:follow:' + #uid", rate = 60, per = 60, failOpen = false)
    public Result<NeedDTO> followNeed(@PathVariable @Positive Long needId) {
        return Result.ok(service.followNeed(needId, UserContext.require()));
    }

    @DeleteMapping("/needs/{needId}/follow")
    @RateLimit(key = "'collaboration:need:unfollow:' + #uid", rate = 60, per = 60, failOpen = false)
    public Result<NeedDTO> unfollowNeed(@PathVariable @Positive Long needId) {
        return Result.ok(service.unfollowNeed(needId, UserContext.require()));
    }

    @PostMapping("/needs/{needId}/claim")
    @RateLimit(key = "'collaboration:need:claim:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<NeedDTO> claimNeed(@PathVariable @Positive Long needId,
                                     @Valid @RequestBody(required = false) NeedClaimCmd cmd) {
        return Result.ok(service.claimNeed(needId, cmd, UserContext.require()));
    }

    @PostMapping("/needs/{needId}/merge")
    @RateLimit(key = "'collaboration:need:merge:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<NeedDTO> mergeNeed(@PathVariable @Positive Long needId,
                                     @Valid @RequestBody NeedMergeCmd cmd) {
        return Result.ok(service.mergeNeed(needId, cmd, UserContext.require()));
    }

    @PostMapping("/needs/{needId}/fulfill")
    @RateLimit(key = "'collaboration:need:fulfill:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<NeedDTO> fulfillNeed(@PathVariable @Positive Long needId,
                                       @Valid @RequestBody NeedCompleteCmd cmd) {
        return Result.ok(service.fulfillNeed(needId, cmd, UserContext.require()));
    }

    @PostMapping("/needs/{needId}/submit")
    @RateLimit(key = "'collaboration:need:submit:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<NeedDTO> submitNeed(@PathVariable @Positive Long needId,
                                      @Valid @RequestBody NeedSubmitCmd cmd) {
        return Result.ok(service.submitNeed(needId, cmd, UserContext.require()));
    }

    @PostMapping("/needs/{needId}/accept")
    @RateLimit(key = "'collaboration:need:accept:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<NeedDTO> acceptNeed(@PathVariable @Positive Long needId,
                                      @Valid @RequestBody(required = false) NeedAcceptCmd cmd) {
        return Result.ok(service.acceptNeed(needId, cmd, UserContext.require()));
    }

    @PostMapping("/needs/{needId}/reject")
    @RateLimit(key = "'collaboration:need:reject:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<NeedDTO> rejectNeed(@PathVariable @Positive Long needId,
                                      @Valid @RequestBody NeedRejectCmd cmd) {
        return Result.ok(service.rejectNeed(needId, cmd, UserContext.require()));
    }

    @PostMapping("/needs/{needId}/withdraw")
    @RateLimit(key = "'collaboration:need:withdraw:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<NeedDTO> withdrawNeed(@PathVariable @Positive Long needId) {
        return Result.ok(service.withdrawNeed(needId, UserContext.require()));
    }

    @PostMapping("/needs/{needId}/release")
    @RateLimit(key = "'collaboration:need:release:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<NeedDTO> releaseNeed(@PathVariable @Positive Long needId,
                                      @Valid @RequestBody(required = false) NeedReleaseCmd cmd) {
        return Result.ok(service.releaseNeed(needId, cmd, UserContext.require()));
    }

    @PostMapping("/needs/{needId}/close")
    @RateLimit(key = "'collaboration:need:close:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<NeedDTO> closeNeed(@PathVariable @Positive Long needId,
                                     @Valid @RequestBody CloseCmd cmd) {
        return Result.ok(service.closeNeed(needId, cmd, UserContext.require()));
    }

    @PublicApi
    @GetMapping("/series")
    @RateLimit(key = "'public:collaboration:series:' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<SeriesDTO>> listSeries(
            @RequestParam(required = false) @Min(1) @Max(5) Integer domain,
            @RequestParam(required = false) @Size(max = 24) String status,
            @RequestParam(defaultValue = "0") @Min(0) long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            HttpServletRequest request) {
        return Result.ok(service.listSeries(domain, status, UserContext.get(), cursor, size));
    }

    @PublicApi
    @GetMapping("/series/{seriesId}")
    @RateLimit(key = "'public:collaboration:series:' + #seriesId + ':' + #request.remoteAddr", rate = 180, per = 60, failOpen = false)
    public Result<SeriesDTO> getSeries(@PathVariable @Positive Long seriesId, HttpServletRequest request) {
        return Result.ok(service.getSeries(seriesId, UserContext.get()));
    }

    @PostMapping("/series")
    @RateLimit(key = "'collaboration:series:create:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<SeriesDTO> createSeries(@Valid @RequestBody SeriesCreateCmd cmd) {
        return Result.ok(service.createSeries(cmd, UserContext.require()));
    }

    @PostMapping("/series/{seriesId}/members")
    @RateLimit(key = "'collaboration:series:member-add:' + #uid", rate = 30, per = 300, failOpen = false)
    public Result<SeriesDTO> addSeriesMember(@PathVariable @Positive Long seriesId,
                                             @Valid @RequestBody SeriesMemberCmd cmd) {
        return Result.ok(service.addSeriesMember(seriesId, cmd, UserContext.require()));
    }

    @DeleteMapping("/series/{seriesId}/members/{memberUid}")
    @RateLimit(key = "'collaboration:series:member-remove:' + #uid", rate = 30, per = 300, failOpen = false)
    public Result<SeriesDTO> removeSeriesMember(@PathVariable @Positive Long seriesId,
                                                @PathVariable @Positive Long memberUid) {
        return Result.ok(service.removeSeriesMember(seriesId, memberUid, UserContext.require()));
    }

    @DeleteMapping("/series/{seriesId}/members/me")
    @RateLimit(key = "'collaboration:series:member-exit:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<SeriesDTO> exitSeries(@PathVariable @Positive Long seriesId) {
        return Result.ok(service.exitSeries(seriesId, UserContext.require()));
    }

    @PublicApi
    @GetMapping("/series/{seriesId}/members")
    @RateLimit(key = "'public:collaboration:series-members:' + #seriesId + ':' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<SeriesMemberDTO>> listSeriesMembers(
            @PathVariable @Positive Long seriesId,
            @RequestParam(defaultValue = "0") @Min(0) long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            HttpServletRequest request) {
        return Result.ok(service.listSeriesMembers(seriesId, UserContext.get(), cursor, size));
    }

    @PostMapping("/series/{seriesId}/submissions")
    @RateLimit(key = "'collaboration:series:submit:' + #uid", rate = 30, per = 300, failOpen = false)
    public Result<SubmissionDTO> submitSeriesPost(@PathVariable @Positive Long seriesId,
                                                   @Valid @RequestBody PostSubmissionCmd cmd) {
        return Result.ok(service.submitSeriesPost(seriesId, cmd, UserContext.require()));
    }

    @PublicApi
    @GetMapping("/series/{seriesId}/submissions")
    @RateLimit(key = "'public:collaboration:series-submissions:' + #seriesId + ':' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<SubmissionDTO>> listSeriesSubmissions(
            @PathVariable @Positive Long seriesId,
            @RequestParam(required = false) @Size(max = 24) String status,
            @RequestParam(defaultValue = "0") @Min(0) long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            HttpServletRequest request) {
        return Result.ok(service.listSeriesSubmissions(seriesId, status, UserContext.get(), cursor, size));
    }

    @PostMapping("/series/{seriesId}/submissions/{submissionId}/decide")
    @RateLimit(key = "'collaboration:series:decide:' + #uid", rate = 60, per = 300, failOpen = false)
    public Result<SubmissionDTO> decideSeriesSubmission(
            @PathVariable @Positive Long seriesId,
            @PathVariable @Positive Long submissionId,
            @Valid @RequestBody ReviewCmd cmd) {
        return Result.ok(service.decideSeriesSubmission(seriesId, submissionId, cmd, UserContext.require()));
    }

    @PostMapping("/series/{seriesId}/close")
    @RateLimit(key = "'collaboration:series:close:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<SeriesDTO> closeSeries(@PathVariable @Positive Long seriesId) {
        return Result.ok(service.closeSeries(seriesId, UserContext.require()));
    }

    @PublicApi
    @GetMapping("/activities")
    @RateLimit(key = "'public:collaboration:activities:' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<ActivityDTO>> listActivities(
            @RequestParam(required = false) @Min(1) @Max(5) Integer domain,
            @RequestParam(required = false) @Size(max = 24) String status,
            @RequestParam(defaultValue = "0") @Min(0) long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            HttpServletRequest request) {
        return Result.ok(service.listActivities(domain, status, UserContext.get(), cursor, size));
    }

    @PublicApi
    @GetMapping("/activities/{activityId}")
    @RateLimit(key = "'public:collaboration:activity:' + #activityId + ':' + #request.remoteAddr", rate = 180, per = 60, failOpen = false)
    public Result<ActivityDTO> getActivity(@PathVariable @Positive Long activityId, HttpServletRequest request) {
        return Result.ok(service.getActivity(activityId, UserContext.get()));
    }

    @PostMapping("/activities")
    @RateLimit(key = "'collaboration:activity:create:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<ActivityDTO> createActivity(@Valid @RequestBody ActivityCreateCmd cmd) {
        return Result.ok(service.createActivity(cmd, UserContext.require()));
    }

    @PutMapping("/activities/{activityId}/status")
    @RateLimit(key = "'collaboration:activity:status:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<ActivityDTO> updateActivityStatus(@PathVariable @Positive Long activityId,
                                                    @Valid @RequestBody ActivityStatusCmd cmd) {
        return Result.ok(service.updateActivityStatus(activityId, cmd, UserContext.require()));
    }

    @PutMapping("/activities/{activityId}/summary")
    @RateLimit(key = "'collaboration:activity:summary:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<ActivityDTO> summarizeActivity(@PathVariable @Positive Long activityId,
                                                 @Valid @RequestBody ActivitySummaryCmd cmd) {
        return Result.ok(service.summarizeActivity(activityId, cmd, UserContext.require()));
    }

    @PostMapping("/activities/{activityId}/submissions")
    @RateLimit(key = "'collaboration:activity:submit:' + #uid", rate = 30, per = 300, failOpen = false)
    public Result<SubmissionDTO> submitActivityPost(@PathVariable @Positive Long activityId,
                                                     @Valid @RequestBody PostSubmissionCmd cmd) {
        return Result.ok(service.submitActivityPost(activityId, cmd, UserContext.require()));
    }

    @PublicApi
    @GetMapping("/activities/{activityId}/submissions")
    @RateLimit(key = "'public:collaboration:activity-submissions:' + #activityId + ':' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<SubmissionDTO>> listActivitySubmissions(
            @PathVariable @Positive Long activityId,
            @RequestParam(required = false) @Size(max = 24) String status,
            @RequestParam(defaultValue = "0") @Min(0) long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            HttpServletRequest request) {
        return Result.ok(service.listActivitySubmissions(activityId, status, UserContext.get(), cursor, size));
    }

    @PostMapping("/activities/{activityId}/submissions/{submissionId}/decide")
    @RateLimit(key = "'collaboration:activity:decide:' + #uid", rate = 60, per = 300, failOpen = false)
    public Result<SubmissionDTO> decideActivitySubmission(
            @PathVariable @Positive Long activityId,
            @PathVariable @Positive Long submissionId,
            @Valid @RequestBody ReviewCmd cmd) {
        return Result.ok(service.decideActivitySubmission(activityId, submissionId, cmd, UserContext.require()));
    }

    @PostMapping("/curation")
    @RateLimit(key = "'collaboration:curation:create:' + #uid", rate = 30, per = 300, failOpen = false)
    public Result<CurationSuggestionDTO> createCurationSuggestion(
            @Valid @RequestBody CurationSuggestionCreateCmd cmd) {
        return Result.ok(service.createCurationSuggestion(cmd, UserContext.require()));
    }

    @GetMapping("/curation/mine")
    @RateLimit(key = "'collaboration:curation:mine:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<CurationSuggestionDTO>> listMyCurationSuggestions(
            @RequestParam(required = false) @Size(max = 24) String status,
            @RequestParam(defaultValue = "0") @Min(0) long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(service.listMyCurationSuggestions(UserContext.require(), status, cursor, size));
    }

    @GetMapping("/curation/review-queue")
    @RateLimit(key = "'collaboration:curation:review-list:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<CurationSuggestionDTO>> listCurationReviewQueue(
            @RequestParam(required = false) @Min(1) @Max(5) Integer domain,
            @RequestParam(required = false) @Size(max = 24) String status,
            @RequestParam(defaultValue = "0") @Min(0) long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(service.listCurationReviewQueue(domain, status, UserContext.require(), cursor, size));
    }

    @PostMapping("/curation/{suggestionId}/decide")
    @RateLimit(key = "'collaboration:curation:decide:' + #uid", rate = 60, per = 300, failOpen = false)
    public Result<CurationSuggestionDTO> decideCurationSuggestion(
            @PathVariable @Positive Long suggestionId,
            @Valid @RequestBody ReviewCmd cmd) {
        return Result.ok(service.decideCurationSuggestion(suggestionId, cmd, UserContext.require()));
    }

    @PublicApi
    @GetMapping("/office-hours")
    @RateLimit(key = "'public:collaboration:office-hours:' + #request.remoteAddr",
            rate = 120, per = 60, failOpen = false)
    public Result<PageResult<OfficeHourDTO>> listOfficeHours(
            @RequestParam(required = false) @Min(1) @Max(5) Integer domain,
            @RequestParam(required = false) @Size(max = 24) String status,
            @RequestParam(defaultValue = "0") @Min(0) long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            HttpServletRequest request) {
        return Result.ok(service.listOfficeHours(domain, status, UserContext.get(), cursor, size));
    }

    @PublicApi
    @GetMapping("/office-hours/{officeHourId}")
    @RateLimit(key = "'public:collaboration:office-hour:' + #officeHourId + ':' + #request.remoteAddr",
            rate = 180, per = 60, failOpen = false)
    public Result<OfficeHourDTO> getOfficeHour(@PathVariable @Positive Long officeHourId,
                                               HttpServletRequest request) {
        return Result.ok(service.getOfficeHour(officeHourId, UserContext.get()));
    }

    @PostMapping("/office-hours")
    @RateLimit(key = "'collaboration:office-hour:create:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<OfficeHourDTO> createOfficeHour(@Valid @RequestBody OfficeHourCreateCmd cmd) {
        return Result.ok(service.createOfficeHour(cmd, UserContext.require()));
    }

    @PutMapping("/office-hours/{officeHourId}/status")
    @RateLimit(key = "'collaboration:office-hour:status:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<OfficeHourDTO> updateOfficeHourStatus(
            @PathVariable @Positive Long officeHourId,
            @Valid @RequestBody OfficeHourStatusCmd cmd) {
        return Result.ok(service.updateOfficeHourStatus(officeHourId, cmd, UserContext.require()));
    }

    @PostMapping("/office-hours/{officeHourId}/reservations")
    @RateLimit(key = "'collaboration:office-hour:reserve:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<OfficeHourReservationDTO> reserveOfficeHour(
            @PathVariable @Positive Long officeHourId,
            @Valid @RequestBody OfficeHourReservationCreateCmd cmd) {
        return Result.ok(service.reserveOfficeHour(officeHourId, cmd, UserContext.require()));
    }

    @GetMapping("/office-hours/{officeHourId}/reservations")
    @RateLimit(key = "'collaboration:office-hour:reservations:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<OfficeHourReservationDTO>> listOfficeHourReservations(
            @PathVariable @Positive Long officeHourId,
            @RequestParam(required = false) @Size(max = 24) String status,
            @RequestParam(defaultValue = "0") @Min(0) long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(service.listOfficeHourReservations(
                officeHourId, status, UserContext.require(), cursor, size));
    }

    @GetMapping("/office-hour-reservations/mine")
    @RateLimit(key = "'collaboration:office-hour:reservations-mine:' + #uid",
            rate = 120, per = 60, failOpen = false)
    public Result<PageResult<OfficeHourReservationDTO>> listMyOfficeHourReservations(
            @RequestParam(required = false) @Size(max = 24) String status,
            @RequestParam(defaultValue = "0") @Min(0) long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(service.listMyOfficeHourReservations(
                UserContext.require(), status, cursor, size));
    }

    @PostMapping("/office-hours/{officeHourId}/reservations/{reservationId}/decide")
    @RateLimit(key = "'collaboration:office-hour:reservation-decide:' + #uid",
            rate = 40, per = 300, failOpen = false)
    public Result<OfficeHourReservationDTO> decideOfficeHourReservation(
            @PathVariable @Positive Long officeHourId,
            @PathVariable @Positive Long reservationId,
            @Valid @RequestBody OfficeHourReservationDecisionCmd cmd) {
        return Result.ok(service.decideOfficeHourReservation(
                officeHourId, reservationId, cmd, UserContext.require()));
    }

    @PostMapping("/office-hours/{officeHourId}/reservations/{reservationId}/cancel")
    @RateLimit(key = "'collaboration:office-hour:reservation-cancel:' + #uid",
            rate = 30, per = 300, failOpen = false)
    public Result<OfficeHourReservationDTO> cancelOfficeHourReservation(
            @PathVariable @Positive Long officeHourId,
            @PathVariable @Positive Long reservationId) {
        return Result.ok(service.cancelOfficeHourReservation(
                officeHourId, reservationId, UserContext.require()));
    }

    @PostMapping("/office-hours/{officeHourId}/reservations/{reservationId}/complete")
    @RateLimit(key = "'collaboration:office-hour:reservation-complete:' + #uid",
            rate = 30, per = 300, failOpen = false)
    public Result<OfficeHourReservationDTO> confirmOfficeHourReservation(
            @PathVariable @Positive Long officeHourId,
            @PathVariable @Positive Long reservationId) {
        return Result.ok(service.confirmOfficeHourReservation(
                officeHourId, reservationId, UserContext.require()));
    }

    @PostMapping("/office-hours/{officeHourId}/reservations/{reservationId}/feedback")
    @RateLimit(key = "'collaboration:office-hour:feedback:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<OfficeHourFeedbackDTO> createOfficeHourFeedback(
            @PathVariable @Positive Long officeHourId,
            @PathVariable @Positive Long reservationId,
            @Valid @RequestBody OfficeHourFeedbackCreateCmd cmd) {
        return Result.ok(service.createOfficeHourFeedback(
                officeHourId, reservationId, cmd, UserContext.require()));
    }

    @GetMapping("/office-hours/{officeHourId}/reservations/{reservationId}/feedback")
    @RateLimit(key = "'collaboration:office-hour:feedback-list:' + #uid",
            rate = 120, per = 60, failOpen = false)
    public Result<List<OfficeHourFeedbackDTO>> listOfficeHourFeedback(
            @PathVariable @Positive Long officeHourId,
            @PathVariable @Positive Long reservationId) {
        return Result.ok(service.listOfficeHourFeedback(
                officeHourId, reservationId, UserContext.require()));
    }

    @PublicApi
    @GetMapping("/discussions")
    @RateLimit(key = "'public:collaboration:discussions:' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<DiscussionDTO>> listDiscussions(
            @RequestParam(required = false) @Min(1) @Max(5) Integer domain,
            @RequestParam(required = false) @Size(max = 24) String status,
            @RequestParam(required = false) @Positive Long postId,
            @RequestParam(defaultValue = "0") @Min(0) long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            HttpServletRequest request) {
        return Result.ok(service.listDiscussions(domain, status, postId, UserContext.get(), cursor, size));
    }

    @PublicApi
    @GetMapping("/discussions/{discussionId}")
    @RateLimit(key = "'public:collaboration:discussion:' + #discussionId + ':' + #request.remoteAddr", rate = 180, per = 60, failOpen = false)
    public Result<DiscussionDTO> getDiscussion(@PathVariable @Positive Long discussionId,
                                               HttpServletRequest request) {
        return Result.ok(service.getDiscussion(discussionId, UserContext.get()));
    }

    @PostMapping("/discussions")
    @RateLimit(key = "'collaboration:discussion:create:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<DiscussionDTO> createDiscussion(@Valid @RequestBody DiscussionCreateCmd cmd) {
        return Result.ok(service.createDiscussion(cmd, UserContext.require()));
    }

    @PostMapping("/discussions/{discussionId}/vote")
    @RateLimit(key = "'collaboration:discussion:vote:' + #uid", rate = 60, per = 60, failOpen = false)
    public Result<DiscussionDTO> voteDiscussion(@PathVariable @Positive Long discussionId,
                                                @Valid @RequestBody VoteCmd cmd) {
        return Result.ok(service.voteDiscussion(discussionId, cmd, UserContext.require()));
    }

    @PutMapping("/discussions/{discussionId}/summary")
    @RateLimit(key = "'collaboration:discussion:summary:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<DiscussionDTO> summarizeDiscussion(@PathVariable @Positive Long discussionId,
                                                     @Valid @RequestBody DiscussionSummaryCmd cmd) {
        return Result.ok(service.summarizeDiscussion(discussionId, cmd, UserContext.require()));
    }

    @PostMapping("/governance/cases")
    @RateLimit(key = "'collaboration:governance:create:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<GovernanceCaseDTO> createGovernanceCase(
            @Valid @RequestBody GovernanceCaseCreateCmd cmd) {
        return Result.ok(service.createGovernanceCase(cmd, UserContext.require()));
    }

    @GetMapping("/governance/cases/mine")
    @RateLimit(key = "'collaboration:governance:mine:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<GovernanceCaseDTO>> listMyGovernanceCases(
            @RequestParam(required = false) @Size(max = 24) String status,
            @RequestParam(defaultValue = "0") @Min(0) long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(service.listMyGovernanceCases(UserContext.require(), status, cursor, size));
    }

    @GetMapping("/governance/cases/review-queue")
    @RateLimit(key = "'collaboration:governance:review-list:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<GovernanceCaseDTO>> listGovernanceReviewQueue(
            @RequestParam(required = false) @Size(max = 24) String status,
            @RequestParam(defaultValue = "0") @Min(0) long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(service.listGovernanceReviewQueue(UserContext.require(), status, cursor, size));
    }

    @PostMapping("/governance/cases/{caseId}/decide")
    @RateLimit(key = "'collaboration:governance:decide:' + #uid", rate = 60, per = 300, failOpen = false)
    public Result<GovernanceCaseDTO> decideGovernanceCase(
            @PathVariable @Positive Long caseId,
            @Valid @RequestBody ReviewCmd cmd) {
        return Result.ok(service.decideGovernanceCase(caseId, cmd, UserContext.require()));
    }
}
