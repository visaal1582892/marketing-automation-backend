package com.medplus.marketing_automation_backend.service;

import com.medplus.marketing_automation_backend.domain.Campaign;
import com.medplus.marketing_automation_backend.domain.User;
import com.medplus.marketing_automation_backend.domain.WorkTask;
import com.medplus.marketing_automation_backend.dto.*;
import com.medplus.marketing_automation_backend.enums.CampaignStatus;
import com.medplus.marketing_automation_backend.enums.MasterTableType;
import com.medplus.marketing_automation_backend.enums.Priority;
import com.medplus.marketing_automation_backend.enums.TaskStatus;
import com.medplus.marketing_automation_backend.exception.BadRequestException;
import com.medplus.marketing_automation_backend.exception.ResourceNotFoundException;
import com.medplus.marketing_automation_backend.domain.ApprovalLog;
import com.medplus.marketing_automation_backend.enums.ApprovalAction;
import com.medplus.marketing_automation_backend.event.TaskAssignedEvent;
import com.medplus.marketing_automation_backend.event.TaskHeldByManagerEvent;
import com.medplus.marketing_automation_backend.event.TaskCancelledEvent;
import com.medplus.marketing_automation_backend.event.CampaignDeletedEvent;
import com.medplus.marketing_automation_backend.event.CommentRespondedEvent;
import com.medplus.marketing_automation_backend.repository.ApprovalLogRepository;
import org.springframework.context.ApplicationEventPublisher;
import com.medplus.marketing_automation_backend.repository.AutoCreatedTaskRepository;
import com.medplus.marketing_automation_backend.repository.CampaignBookmarkRepository;
import com.medplus.marketing_automation_backend.repository.CampaignRepository;
import com.medplus.marketing_automation_backend.repository.UserRepository;
import com.medplus.marketing_automation_backend.repository.WorkTaskRepository;
import com.medplus.marketing_automation_backend.repository.WorkerCommentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CampaignService {

    private final CampaignRepository         campaignRepo;
    private final WorkTaskRepository         workTaskRepo;
    private final UserRepository             userRepo;
    private final RoutingEngineService       routingEngine;
    private final QuestionnaireService       questionnaireService;
    private final MasterDataService          masterDataService;
    private final CampaignBookmarkRepository bookmarkRepo;
    private final WorkerCommentRepository    workerCommentRepo;
    private final ApprovalLogRepository      approvalLogRepo;
    private final AutoCreatedTaskRepository  autoCreatedTaskRepo;
    private final AutoCreatedTaskService     autoCreatedTaskService;
    private final ApplicationEventPublisher  eventPublisher;
    private final NotificationService        notificationService;

    public CampaignService(CampaignRepository campaignRepo,
                           WorkTaskRepository workTaskRepo,
                           UserRepository userRepo,
                           RoutingEngineService routingEngine,
                           QuestionnaireService questionnaireService,
                           MasterDataService masterDataService,
                           CampaignBookmarkRepository bookmarkRepo,
                           WorkerCommentRepository workerCommentRepo,
                           ApprovalLogRepository approvalLogRepo,
                           AutoCreatedTaskRepository autoCreatedTaskRepo,
                           AutoCreatedTaskService autoCreatedTaskService,
                           ApplicationEventPublisher eventPublisher,
                           NotificationService notificationService) {
        this.campaignRepo           = campaignRepo;
        this.workTaskRepo           = workTaskRepo;
        this.userRepo               = userRepo;
        this.routingEngine          = routingEngine;
        this.questionnaireService   = questionnaireService;
        this.masterDataService      = masterDataService;
        this.bookmarkRepo           = bookmarkRepo;
        this.workerCommentRepo      = workerCommentRepo;
        this.approvalLogRepo        = approvalLogRepo;
        this.autoCreatedTaskRepo    = autoCreatedTaskRepo;
        this.autoCreatedTaskService  = autoCreatedTaskService;
        this.eventPublisher         = eventPublisher;
        this.notificationService    = notificationService;
    }

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    @Transactional
    public CampaignResponse create(CampaignRequest req, User requestor) {
        log.info("CAMPAIGN create | requestorId={} requestorEmail={} priority={} department={}",
                requestor.getUserId(), requestor.getEmail(), req.getPriority(), req.getDepartmentId());

        // Module 2-C: Inconsistency flagging — e.g. HIGH/Urgent priority + low budget.
        String inconsistencyReason = detectInconsistency(req.getPriority(), req.getBudgetTier());

        Campaign campaign = Campaign.builder()
                .requestorId(requestor.getUserId().intValue())
                .departmentId(req.getDepartmentId())
                .targetLocation(req.getTargetLocation())
                .businessObjective(req.getBusinessObjective())
                .campaignTypeId(req.getCampaignTypeId())
                .businessVerticalId(req.getBusinessVerticalId())
                .businessTypeId(req.getBusinessTypeId())
                .storeFormatTypeId(req.getStoreFormatTypeId())
                .storeId(req.getStoreId())
                .contactNumber(req.getContactNumber())
                .audienceTypeId(masterDataService.toJsonArray(req.getAudienceTypeId()))
                .language(masterDataService.toJsonArray(req.getLanguage()))
                .hasOffer(req.getHasOffer())
                .offerTypeId("YES".equals(req.getHasOffer()) ? req.getOfferTypeId() : null)
                .keyMessage(req.getKeyMessage())
                .supportingProof(req.getSupportingProof())
                .tone(masterDataService.toJsonArray(req.getTone()))
                .priority(req.getPriority())
                .budgetTier(req.getBudgetTier())
                .vendorRequired(req.getVendorRequired())
                .vendorType("YES".equals(req.getVendorRequired())
                        ? masterDataService.toJsonArray(req.getVendorType()) : null)
                .kpiType(req.getKpiType())
                .expectedOutput(req.getExpectedOutput())
                .status(CampaignStatus.IN_PROGRESS)
                .flaggedInconsistency(inconsistencyReason != null)
                .inconsistencyReason(inconsistencyReason)
                .build();

        if (inconsistencyReason != null) {
            log.warn("CAMPAIGN inconsistency flagged | priority={} budget={} reason={}",
                    req.getPriority(), req.getBudgetTier(), inconsistencyReason);
        }

        Integer campaignId = campaignRepo.insert(campaign);
        log.debug("CAMPAIGN inserted | campaignId={}", campaignId);

        // Save campaign-level supporting files
        if (req.getFileUrls() != null) {
            List<String> origNames = req.getFileOriginalNames();
            for (int fi = 0; fi < req.getFileUrls().size(); fi++) {
                String url = req.getFileUrls().get(fi);
                if (url != null && !url.isBlank()) {
                    String fileName = (origNames != null && fi < origNames.size() && origNames.get(fi) != null)
                            ? origNames.get(fi)
                            : (url.contains("/") ? url.substring(url.lastIndexOf('/') + 1) : url);
                    campaignRepo.insertCampaignFile(campaignId, url, fileName);
                }
            }
        }

        // Auto-route directly to workers — no approval steps required.
        // If team capacity is full, InsufficientCapacityException is thrown
        // and the whole transaction rolls back (campaign is not created).
        List<String> newGranularTaskIds = new java.util.ArrayList<>();
        Map<String, List<WorkTaskAnswerRequest.AnswerItem>> qa = new LinkedHashMap<>();
        if (req.getTaskSpecs() != null) {
            for (TaskSpecRequest spec : req.getTaskSpecs()) {
                if (spec.getGranularTaskId() != null) {
                    newGranularTaskIds.add(spec.getGranularTaskId());
                    if (spec.getQuestionnaireAnswers() != null && !spec.getQuestionnaireAnswers().isEmpty()) {
                        qa.put(spec.getGranularTaskId(), spec.getQuestionnaireAnswers());
                    }
                }
            }
        }
        routingEngine.route(campaignId, newGranularTaskIds, qa, true);

        log.info("CAMPAIGN created successfully | campaignId={} requestorId={}",
                campaignId, requestor.getUserId());
        return getDetail(campaignId);
    }

    /**
     * Returns a human-readable reason if the brief has an illogical combination
     * of priority + budget, otherwise {@code null}. (Module 2-C: Inconsistency Flagging.)
     * Budget may now be a master-item ID or a free-text "Other" value — we check
     * both known low-budget IDs ("1","2") and display-name fallbacks.
     */
    static String detectInconsistency(Priority priority, String budget) {
        if (priority == null || budget == null || budget.isBlank()) return null;
        // Known low-budget master IDs and legacy display names
        boolean lowBudget = budget.equals("1") || budget.equals("2")
                || budget.equals("No Budget (Organic)") || budget.equals("< Rs.50K");
        if (priority == Priority.HIGH && lowBudget) {
            return "High/Urgent priority requested with very low budget (budget tier: " + budget + ").";
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    public CampaignResponse getDetail(int campaignId) {
        Campaign c = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + campaignId));
        return toDetailResponse(c, null);
    }

    /** Detail with bookmark flag enriched for the given viewer. */
    public CampaignResponse getDetail(int campaignId, int viewerUserId) {
        Campaign c = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + campaignId));
        CampaignResponse r = toDetailResponse(c, viewerUserId);
        r.setBookmarked(bookmarkRepo.isBookmarked(viewerUserId, campaignId));
        return r;
    }

    public List<CampaignResponse> listAll(boolean includeInactive) {
        return campaignRepo.findAll(includeInactive).stream()
                .map(this::toSummaryResponse).collect(Collectors.toList());
    }

    public List<CampaignResponse> listMy(int requestorId) {
        Set<Integer> bookmarked = bookmarkRepo.findBookmarkedCampaignIds(requestorId);
        return campaignRepo.findByRequestorId(requestorId).stream()
                .map(c -> {
                    CampaignResponse r = toSummaryResponse(c);
                    r.setBookmarked(bookmarked.contains(c.getCampaignId()));
                    return r;
                })
                .collect(Collectors.toList());
    }

    /** Paginated campaign list for a requestor with optional column filters. */
    public PagedResponse<CampaignResponse> listMyPaged(
            int requestorId,
            String campaignId, String status, String priority,
            String taskType,
            LocalDate dateFrom, LocalDate dateTo,
            int page, int size) {

        Set<Integer> bookmarked = bookmarkRepo.findBookmarkedCampaignIds(requestorId);
        PagedResponse<Campaign> raw = campaignRepo.findByRequestorIdPaged(
                requestorId, campaignId, status, priority, taskType, dateFrom, dateTo, page, size);

        List<CampaignResponse> mapped = raw.content().stream()
                .map(c -> {
                    CampaignResponse r = toSummaryResponse(c);
                    r.setBookmarked(bookmarked.contains(c.getCampaignId()));
                    return r;
                })
                .collect(Collectors.toList());
        return PagedResponse.of(mapped, raw.totalElements(), raw.page(), raw.size());
    }

    /** Returns only the campaigns bookmarked by this user, in bookmark order. */
    public List<CampaignResponse> listBookmarked(int userId) {
        java.util.Set<Integer> ids = bookmarkRepo.findBookmarkedCampaignIds(userId);
        return campaignRepo.findByRequestorId(userId).stream()
                .filter(c -> ids.contains(c.getCampaignId()))
                .map(c -> {
                    CampaignResponse r = toSummaryResponse(c);
                    r.setBookmarked(true);
                    return r;
                })
                .collect(Collectors.toList());
    }

    /**
     * Toggles a bookmark for the given user + campaign.
     * @return {@code true} if the campaign is now bookmarked, {@code false} if removed.
     */
    public boolean toggleBookmark(int userId, int campaignId) {
        return bookmarkRepo.toggle(userId, campaignId);
    }

    /**
     * Returns every COMPLETED work task across the given requestor's campaigns.
     * Used by the requestor's "Completed Tasks" page — avoids the N+1 problem
     * of loading each campaign's detail separately just to pick out its tasks.
     */
    public List<WorkTaskResponse> listCompletedTasksForRequestor(int requestorId) {
        return workTaskRepo.findCompletedByRequestorId(requestorId).stream()
                .map(CampaignService::toWorkTaskResponse)
                .collect(Collectors.toList());
    }

    /** Paginated completed tasks for a requestor with optional column filters. */
    public PagedResponse<WorkTaskResponse> listCompletedTasksForRequestorPaged(
            int requestorId,
            String campaignId, String taskId, String taskName,
            String taskType, String completedBy,
            LocalDate dateFrom, LocalDate dateTo,
            int page, int size) {

        PagedResponse<WorkTask> raw = workTaskRepo.findCompletedByRequestorIdPaged(
                requestorId, campaignId, taskId, taskName, taskType, completedBy,
                dateFrom, dateTo, page, size);

        List<WorkTaskResponse> mapped = raw.content().stream()
                .map(CampaignService::toWorkTaskResponse)
                .collect(Collectors.toList());
        return PagedResponse.of(mapped, raw.totalElements(), raw.page(), raw.size());
    }

    /**
     * Paged, filterable REQUESTOR_QC_REVIEW tasks for the caller's campaigns.
     * Admins see all tasks regardless of requestor.
     */
    public PagedResponse<WorkTaskResponse> listRequestorQcTasks(
            int requestorId, boolean isAdmin,
            String search, java.time.LocalDate dateFrom, java.time.LocalDate dateTo,
            int page, int size) {
        PagedResponse<WorkTask> raw = workTaskRepo.findRequestorQcReviewPaged(
                requestorId, isAdmin, search, dateFrom, dateTo, page, size);
        List<WorkTaskResponse> mapped = raw.content().stream()
                .map(CampaignService::toWorkTaskResponse)
                .collect(Collectors.toList());
        return PagedResponse.of(mapped, raw.totalElements(), raw.page(), raw.size());
    }

    /**
     * Returns every COMPLETED work task across all campaigns (admin / manager view).
     */
    public List<WorkTaskResponse> listAllCompletedTasks() {
        return workTaskRepo.findAllCompleted().stream()
                .map(CampaignService::toWorkTaskResponse)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Requestor — add new tasks to an existing campaign
    // -------------------------------------------------------------------------

    /**
     * Appends new granular-task deliverables to an existing campaign and
     * immediately routes them to workers. Existing tasks are never touched.
     * Only the original requestor (or Admin / Marketing Manager) may call this.
     * Not allowed on terminal campaigns (COMPLETED / REJECTED / CANCELLED).
     */
    @Transactional
    public CampaignResponse addTasksToCampaign(int campaignId, List<TaskSpecRequest> specs, int requestorId) {
        log.info("CAMPAIGN addTasks | campaignId={} requestorId={} specsCount={}",
                campaignId, requestorId, specs == null ? 0 : specs.size());
        Campaign c = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + campaignId));

        if (c.getRequestorId() != requestorId) {
            throw new BadRequestException("You can only add tasks to your own campaigns.");
        }

        CampaignStatus status = c.getStatus();
        if (status == CampaignStatus.COMPLETED
                || status == CampaignStatus.REJECTED
                || status == CampaignStatus.CANCELLED) {
            throw new BadRequestException("Cannot add tasks to a campaign that is " + status + ".");
        }

        if (specs == null || specs.isEmpty()) {
            throw new BadRequestException("At least one task must be selected.");
        }

        List<String> newGranularTaskIds = new java.util.ArrayList<>();
        Map<String, List<WorkTaskAnswerRequest.AnswerItem>> qa = new LinkedHashMap<>();
        for (TaskSpecRequest spec : specs) {
            if (spec.getGranularTaskId() == null || spec.getGranularTaskId().isBlank()) continue;
            newGranularTaskIds.add(spec.getGranularTaskId());
            if (spec.getQuestionnaireAnswers() != null && !spec.getQuestionnaireAnswers().isEmpty()) {
                qa.put(spec.getGranularTaskId(), spec.getQuestionnaireAnswers());
            }
        }

        // Route only the newly added tasks
        routingEngine.route(campaignId, newGranularTaskIds, qa, true);

        return getDetail(campaignId);
    }

    // -------------------------------------------------------------------------
    // Requestor — followup tasks (allowed even on COMPLETED campaigns)
    // -------------------------------------------------------------------------

    /**
     * Adds new tasks to any non-cancelled/non-rejected campaign, including
     * COMPLETED ones (which get reopened to IN_PROGRESS automatically via routing).
     * Per-spec file URLs are linked to the newly created work task after routing.
     */
    @Transactional
    public CampaignResponse addFollowupTasks(int campaignId, FollowupTaskRequest req, int requestorId) {
        log.info("CAMPAIGN followup-tasks | campaignId={} requestorId={} specsCount={}",
                campaignId, requestorId, req == null || req.getSpecs() == null ? 0 : req.getSpecs().size());
        Campaign c = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + campaignId));

        if (c.getRequestorId() != requestorId) {
            throw new BadRequestException("You can only add tasks to your own campaigns.");
        }
        if (c.getStatus() == CampaignStatus.REJECTED || c.getStatus() == CampaignStatus.CANCELLED) {
            throw new BadRequestException("Cannot add tasks to a campaign that is " + c.getStatus() + ".");
        }
        if (req == null || req.getSpecs() == null || req.getSpecs().isEmpty()) {
            throw new BadRequestException("At least one task must be selected.");
        }

        List<String> newGranularTaskIds = new java.util.ArrayList<>();
        Map<String, List<WorkTaskAnswerRequest.AnswerItem>> qa = new LinkedHashMap<>();
        List<TaskSpecRequest> validSpecs = new java.util.ArrayList<>();
        for (TaskSpecRequest spec : req.getSpecs()) {
            if (spec.getGranularTaskId() == null || spec.getGranularTaskId().isBlank()) continue;
            newGranularTaskIds.add(spec.getGranularTaskId());
            if (spec.getQuestionnaireAnswers() != null && !spec.getQuestionnaireAnswers().isEmpty()) {
                qa.put(spec.getGranularTaskId(), spec.getQuestionnaireAnswers());
            }
            validSpecs.add(spec);
        }

        // Route newly added tasks; auto-assign non-OTHER tasks immediately.
        routingEngine.route(campaignId, newGranularTaskIds, qa, true);

        // Link per-task reference files to the newly created work tasks
        for (TaskSpecRequest spec : validSpecs) {
            List<String> urls  = spec.getFileUrls();
            if (urls == null || urls.isEmpty()) continue;
            List<String> names = spec.getFileOriginalNames();
            workTaskRepo.findLatestByGranularTaskIdAndCampaignId(spec.getGranularTaskId(), campaignId)
                    .ifPresent(wt -> {
                        for (int i = 0; i < urls.size(); i++) {
                            String url  = urls.get(i);
                            String name = (names != null && i < names.size() && names.get(i) != null)
                                    ? names.get(i) : url;
                            if (url != null && !url.isBlank()) {
                                campaignRepo.insertTaskFile(campaignId, wt.getTaskId(), url, name);
                            }
                        }
                    });
        }

        return getDetail(campaignId);
    }

    // -------------------------------------------------------------------------
    // Requestor — edit campaign details + add tasks/files
    // -------------------------------------------------------------------------

    /**
     * Lets the original requestor update the form fields of their own campaign
     * and/or add new task deliverables and supporting files.
     * Existing tasks and files are NEVER removed or changed.
     * Not allowed on terminal campaigns (COMPLETED / REJECTED / CANCELLED).
     */
    @Transactional
    public CampaignResponse editCampaignAsRequestor(int campaignId,
                                                     RequestorCampaignUpdateRequest req,
                                                     int requestorId) {
        log.info("CAMPAIGN edit | campaignId={} requestorId={}", campaignId, requestorId);
        Campaign c = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + campaignId));

        if (c.getRequestorId() != requestorId) {
            throw new BadRequestException("You can only edit your own campaigns.");
        }
        CampaignStatus status = c.getStatus();
        if (status == CampaignStatus.COMPLETED
                || status == CampaignStatus.REJECTED
                || status == CampaignStatus.CANCELLED) {
            throw new BadRequestException("Cannot edit a campaign that is " + status + ".");
        }

        // Resolve updated values, falling back to existing ones when field is null
        String newDeptId          = req.getDepartmentId()       != null ? req.getDepartmentId()       : c.getDepartmentId();
        String newTargetLocation  = req.getTargetLocation()     != null ? req.getTargetLocation()     : c.getTargetLocation();
        String newBizObjective    = req.getBusinessObjective()  != null ? req.getBusinessObjective()  : c.getBusinessObjective();
        String newAudienceTypeId  = req.getAudienceTypeId()     != null
                ? masterDataService.toJsonArray(req.getAudienceTypeId()) : c.getAudienceTypeId();
        String newLanguage        = req.getLanguage()           != null
                ? masterDataService.toJsonArray(req.getLanguage())       : c.getLanguage();
        String newHasOffer        = req.getHasOffer()           != null ? req.getHasOffer()           : c.getHasOffer();
        String newOfferTypeId     = req.getOfferTypeId()        != null ? req.getOfferTypeId()        : c.getOfferTypeId();
        String newKeyMessage      = req.getKeyMessage()         != null ? req.getKeyMessage()         : c.getKeyMessage();
        String newSupportingProof = req.getSupportingProof()    != null ? req.getSupportingProof()    : c.getSupportingProof();
        String newTone            = req.getTone()               != null
                ? masterDataService.toJsonArray(req.getTone())           : c.getTone();
        Priority newPriority      = req.getPriority()           != null ? req.getPriority()           : c.getPriority();
        String newBudgetTier      = req.getBudgetTier()         != null ? req.getBudgetTier()         : c.getBudgetTier();
        String newVendorRequired  = req.getVendorRequired()     != null ? req.getVendorRequired()     : c.getVendorRequired();
        String newVendorType      = req.getVendorType()         != null
                ? ("YES".equals(newVendorRequired) ? masterDataService.toJsonArray(req.getVendorType()) : null)
                : c.getVendorType();
        String newKpiType         = req.getKpiType()            != null ? req.getKpiType()            : c.getKpiType();
        String newExpectedOutput  = req.getExpectedOutput()     != null ? req.getExpectedOutput()     : c.getExpectedOutput();
        String newStoreId         = req.getStoreId()            != null ? req.getStoreId()            : c.getStoreId();
        String newContactNumber   = req.getContactNumber()      != null ? req.getContactNumber()      : c.getContactNumber();

        String reason = detectInconsistency(newPriority, newBudgetTier);

        campaignRepo.updateRequestorFields(
                campaignId,
                newDeptId, newTargetLocation, newBizObjective,
                newStoreId, newContactNumber,
                newAudienceTypeId, newLanguage,
                newHasOffer,
                "YES".equals(newHasOffer) ? newOfferTypeId : null,
                "YES".equals(newHasOffer) ? newKeyMessage  : null,
                "YES".equals(newHasOffer) ? newSupportingProof : null,
                newTone, newPriority, newBudgetTier, newVendorRequired, newVendorType,
                newKpiType, newExpectedOutput,
                reason != null, reason);

        // Add new task deliverables
        if (req.getNewTaskSpecs() != null && !req.getNewTaskSpecs().isEmpty()) {
            List<String> newGranularTaskIds = new java.util.ArrayList<>();
            Map<String, List<WorkTaskAnswerRequest.AnswerItem>> qa = new LinkedHashMap<>();
            List<TaskSpecRequest> validSpecs = new java.util.ArrayList<>();
            for (TaskSpecRequest spec : req.getNewTaskSpecs()) {
                if (spec.getGranularTaskId() == null || spec.getGranularTaskId().isBlank()) continue;
                newGranularTaskIds.add(spec.getGranularTaskId());
                if (spec.getQuestionnaireAnswers() != null && !spec.getQuestionnaireAnswers().isEmpty()) {
                    qa.put(spec.getGranularTaskId(), spec.getQuestionnaireAnswers());
                }
                validSpecs.add(spec);
            }
            routingEngine.route(campaignId, newGranularTaskIds, qa, true);
            // Link per-task reference files to newly created work tasks
            for (TaskSpecRequest spec : validSpecs) {
                List<String> fileUrls = spec.getFileUrls();
                if (fileUrls == null || fileUrls.isEmpty()) continue;
                List<String> fileNames = spec.getFileOriginalNames();
                workTaskRepo.findLatestByGranularTaskIdAndCampaignId(spec.getGranularTaskId(), campaignId)
                        .ifPresent(wt -> {
                            for (int fi = 0; fi < fileUrls.size(); fi++) {
                                String url  = fileUrls.get(fi);
                                String name = (fileNames != null && fi < fileNames.size() && fileNames.get(fi) != null)
                                        ? fileNames.get(fi) : url;
                                if (url != null && !url.isBlank()) {
                                    campaignRepo.insertTaskFile(campaignId, wt.getTaskId(), url, name);
                                }
                            }
                        });
            }
        }

        // Remove files the requestor explicitly deleted
        if (req.getRemovedFileUrls() != null) {
            for (String url : req.getRemovedFileUrls()) {
                if (url != null && !url.isBlank()) {
                    campaignRepo.deleteFileByUrl(campaignId, url);
                }
            }
        }

        // Add new campaign files
        if (req.getNewFileUrls() != null) {
            List<String> origNames = req.getNewFileOriginalNames();
            for (int fi = 0; fi < req.getNewFileUrls().size(); fi++) {
                String url = req.getNewFileUrls().get(fi);
                if (url != null && !url.isBlank()) {
                    String fileName = (origNames != null && fi < origNames.size() && origNames.get(fi) != null)
                            ? origNames.get(fi)
                            : (url.contains("/") ? url.substring(url.lastIndexOf('/') + 1) : url);
                    campaignRepo.insertCampaignFile(campaignId, url, fileName);
                }
            }
        }

        // The requestor's edit implicitly acknowledges any worker hold comments.
        // Restore all HELD tasks to their pre-hold status and mark comments answered.
        String requestorDisplayName = userRepo.findById((long) requestorId)
                .map(u -> u.getFullName() != null ? u.getFullName() : "Requestor")
                .orElse("Requestor");
        workTaskRepo.findByCampaignId(campaignId).stream()
                .filter(wt -> wt.getStatus() == TaskStatus.HELD)
                .forEach(wt -> {
                    workerCommentRepo.markAllAnswered(wt.getTaskId());
                    workTaskRepo.clearHoldByTaskId(wt.getTaskId());
                    // Requestor answered the comment — resolve COMMENT_ADDED notification
                    notificationService.resolveNotifications(wt.getTaskId(), "COMMENT_ADDED");
                    if (wt.getAssignedTo() != null) {
                        eventPublisher.publishEvent(
                                new CommentRespondedEvent(wt.getTaskId(), requestorDisplayName, wt.getAssignedTo()));
                    }
                });

        return getDetail(campaignId);
    }

    // -------------------------------------------------------------------------
    // Marketing Head adjustments
    // -------------------------------------------------------------------------

    /**
     * Marketing Head or Admin updates editable fields of a campaign.
     * Allowed at any non-terminal stage.
     */
    @Transactional
    public CampaignResponse updateCampaign(int campaignId, CampaignUpdateRequest req) {
        Campaign c = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + campaignId));
        if (c.getStatus() == CampaignStatus.COMPLETED || c.getStatus() == CampaignStatus.REJECTED) {
            throw new IllegalStateException("Cannot edit a campaign that is " + c.getStatus());
        }
        Priority newPriority = req.getPriority() != null ? req.getPriority() : c.getPriority();
        String reason = detectInconsistency(newPriority, c.getBudgetTier());
        campaignRepo.updateCampaignFields(campaignId, newPriority, req.getKeyMessage(),
                req.getBudgetTier(), reason != null, reason);
        return getDetail(campaignId);
    }

    /**
     * Marketing Head changes a campaign's priority. Allowed at any non-terminal
     * stage so the head can fast-track an in-flight job (or de-escalate a
     * cooled-down request). Re-evaluates the priority/budget inconsistency
     * flag using the new priority + existing budget tier so the badge stays
     * accurate after the edit.
     */
    @Transactional
    public CampaignResponse updatePriority(int campaignId, Priority newPriority) {
        log.info("CAMPAIGN updatePriority | campaignId={} newPriority={}", campaignId, newPriority);
        if (newPriority == null) {
            throw new IllegalArgumentException("Priority is required");
        }
        Campaign c = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + campaignId));

        // Block edits on terminal states — they're already closed and changing
        // the priority would only confuse the audit trail.
        if (c.getStatus() == CampaignStatus.COMPLETED || c.getStatus() == CampaignStatus.REJECTED) {
            throw new IllegalStateException(
                    "Cannot change priority of a campaign that is " + c.getStatus());
        }

        String reason = detectInconsistency(newPriority, c.getBudgetTier());
        campaignRepo.updatePriority(campaignId, newPriority, reason != null, reason);
        return getDetail(campaignId);
    }

    // -------------------------------------------------------------------------
    // Clone
    // -------------------------------------------------------------------------

    /**
     * Creates a copy of the given campaign for the requesting user.
     * Copies all brief fields but resets status, timestamps, and workflow state.
     * No work-tasks are copied — the clone is a fresh request.
     *
     * @return the new campaign's ID
     */
    @Transactional
    public int cloneCampaign(int sourceCampaignId, int requestorId) {
        Campaign src = campaignRepo.findById(sourceCampaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + sourceCampaignId));

        String inconsistencyReason = detectInconsistency(src.getPriority(),
                src.getBudgetTierId() != null ? src.getBudgetTierId() : src.getBudgetTier());

        Campaign clone = Campaign.builder()
                .requestorId(requestorId)
                .departmentId(src.getDepartmentId())
                .targetLocation(src.getTargetLocation())
                .businessObjective(src.getBusinessObjectiveId() != null ? src.getBusinessObjectiveId() : src.getBusinessObjective())
                .campaignTypeId(src.getCampaignTypeId())
                .businessVerticalId(src.getBusinessVerticalId())
                .businessTypeId(src.getBusinessTypeId())
                .storeFormatTypeId(src.getStoreFormatTypeId())
                .storeId(src.getStoreId())
                .contactNumber(src.getContactNumber())
                .audienceTypeId(src.getAudienceTypeId())
                .language(src.getLanguage())
                .hasOffer(src.getHasOffer())
                .offerTypeId(src.getOfferTypeId())
                .keyMessage(src.getKeyMessage())
                .supportingProof(src.getSupportingProof())
                .tone(src.getTone())
                .priority(src.getPriority())
                .budgetTier(src.getBudgetTierId() != null ? src.getBudgetTierId() : src.getBudgetTier())
                .vendorRequired(src.getVendorRequired())
                .vendorType(src.getVendorType())
                .kpiType(src.getKpiTypeId() != null ? src.getKpiTypeId() : src.getKpiType())
                .expectedOutput(src.getExpectedOutputId() != null ? src.getExpectedOutputId() : src.getExpectedOutput())
                .status(CampaignStatus.IN_PROGRESS)
                .flaggedInconsistency(inconsistencyReason != null)
                .inconsistencyReason(inconsistencyReason)
                .build();

        return campaignRepo.insert(clone);
    }

    // -------------------------------------------------------------------------
    // Hold / Unhold (Module 2-B Capacity Alerts redesign)
    // -------------------------------------------------------------------------

    /**
     * Holds an ASSIGNED task — pulls it out of the assignee's queue so their
     * capacity slot can absorb a higher-priority campaign waiting at the
     * marketing-head approval gate. Refuses if the worker has already
     * started (IN_PROGRESS / REWORK / QC_REVIEW): yanking work mid-flight
     * would waste their effort.
     *
     * <p>The original assignee's {@code current_active_tasks} counter is
     * decremented so subsequent capacity checks see the freed slot.
     */
    @Transactional
    public WorkTaskResponse holdTask(String taskId, int actorId) {
        log.info("TASK hold | taskId={} actorId={}", taskId, actorId);
        WorkTask task = workTaskRepo.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        if (task.getStatus() != TaskStatus.ASSIGNED && task.getStatus() != TaskStatus.REWORK) {
            throw new BadRequestException(
                    "Only ASSIGNED or REWORK tasks can be held — this task is currently "
                            + task.getStatus() + ".");
        }
        int updated = workTaskRepo.markHeld(taskId);
        if (updated == 0) {
            throw new BadRequestException("Could not hold task — refresh and retry.");
        }
        if (task.getAssignedTo() != null) {
            userRepo.decrementActiveTasks(task.getAssignedTo().longValue());
        }
        workTaskRepo.deactivateCollaboration(taskId);
        approvalLogRepo.insert(ApprovalLog.builder()
                .taskId(taskId).reviewerId(actorId)
                .actionTaken(ApprovalAction.HELD).build());
        log.info("TASK held | taskId={} previousAssignee={}", taskId, task.getAssignedTo());
        if (task.getAssignedTo() != null) {
            User actor = userRepo.findById((long) actorId).orElse(null);
            String managerName = actor != null && actor.getFullName() != null ? actor.getFullName() : "Manager";
            eventPublisher.publishEvent(new TaskHeldByManagerEvent(taskId, managerName, task.getAssignedTo()));
        }
        return toWorkTaskResponse(workTaskRepo.findById(taskId).orElseThrow());
    }

    /**
     * Unholds a task and re-routes it through the standard auto-routing
     * pick. The new assignee may differ from the one who was holding it.
     * Throws {@link InsufficientCapacityException} (mapped to 409) if no
     * user in the relevant role is available — the task stays HELD until
     * the manager frees a slot and retries.
     */
    @Transactional
    public WorkTaskResponse unholdTask(String taskId, int actorId) {
        log.info("TASK unhold | taskId={} actorId={}", taskId, actorId);
        routingEngine.unholdAndReassign(taskId);
        approvalLogRepo.insert(ApprovalLog.builder()
                .taskId(taskId).reviewerId(actorId)
                .actionTaken(ApprovalAction.UNHOLD).build());
        // Manager un-held — resolve TASK_HELD_BY_MANAGER notification
        notificationService.resolveNotifications(taskId, "TASK_HELD_BY_MANAGER");
        return toWorkTaskResponse(workTaskRepo.findById(taskId).orElseThrow());
    }

    /**
     * Cancels a task that has not yet been started (ASSIGNED or HELD).
     * Refused for any task already in progress (IN_PROGRESS, REWORK, QC_REVIEW).
     * If this was the last non-terminal task on the campaign, the campaign
     * itself is also set to CANCELLED.
     *
     * <p>The campaign-status update is intentionally excluded from the
     * rollback scope: if the CANCELLED enum value is not yet present in the
     * DB (e.g. V4 migration hasn't been applied on this instance), the task
     * is still cancelled successfully and the campaign update is silently
     * skipped rather than blocking the whole operation.
     */
    @Transactional(noRollbackFor = org.springframework.dao.DataIntegrityViolationException.class)
    public WorkTaskResponse cancelTask(String taskId, int actorId) {
        log.info("TASK cancel | taskId={}", taskId);
        WorkTask task = workTaskRepo.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        TaskStatus s = task.getStatus();
        if (s != TaskStatus.ASSIGNED && s != TaskStatus.HELD && s != TaskStatus.REWORK) {
            throw new BadRequestException(
                    "Only ASSIGNED, HELD or REWORK tasks can be cancelled — this task is " + s + ".");
        }
        workTaskRepo.markCancelled(taskId);
        autoCreatedTaskService.cancelLinkedContentTaskOnSourceCancelledOrRejected(
                taskId, "System (Source task cancelled)");
        log.info("TASK cancelled | taskId={} previousStatus={} assignee={}",
                taskId, s, task.getAssignedTo());
        approvalLogRepo.insert(ApprovalLog.builder()
                .taskId(taskId).reviewerId(actorId)
                .actionTaken(ApprovalAction.CANCELLED).build());
        if (task.getAssignedTo() != null && (s == TaskStatus.ASSIGNED || s == TaskStatus.REWORK)) {
            userRepo.decrementActiveTasks(task.getAssignedTo().longValue());
        }
        if (task.getAssignedTo() != null) {
            eventPublisher.publishEvent(new TaskCancelledEvent(taskId, "Manager", task.getAssignedTo()));
        }
        // Auto-cancel the campaign when all its tasks are now terminal.
        // Wrapped in its own try-catch so a schema mismatch (V4 migration not
        // yet applied) never prevents the task from being cancelled.
        try {
            int remaining = workTaskRepo.countIncomplete(task.getCampaignId());
            if (remaining == 0) {
                campaignRepo.updateStatus(task.getCampaignId(), CampaignStatus.CANCELLED);
            }
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // V4 migration has not been applied on this DB — task is still
            // cancelled; campaign auto-cancel will work after next restart.
        }
        return toWorkTaskResponse(workTaskRepo.findById(taskId).orElseThrow());
    }

    /** Lists every task currently on hold across the system. */
    public List<WorkTaskResponse> listHeldTasks() {
        return workTaskRepo.findHeld().stream()
                .map(CampaignService::toWorkTaskResponse)
                .collect(Collectors.toList());
    }

    /**
     * Returns all active users eligible to work on a specific held task,
     * based on the task's granular-task type and role-task mappings.
     * Used by the manual-assign modal on the Held Tasks page.
     */
    public List<com.medplus.marketing_automation_backend.dto.UserResponse> getEligibleUsersForTask(String taskId) {
        WorkTask task = workTaskRepo.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        return routingEngine.getEligibleUsersForTask(task).stream()
                .map(u -> {
                    UserResponse r = new UserResponse();
                    r.setUserId(u.getUserId());
                    r.setFullName(u.getFullName());
                    r.setEmail(u.getEmail());
                    r.setRole(u.getPrimaryRoleName());
                    r.setRoleIds(u.getRoleIds());
                    r.setRoleNames(u.getRoleNames());
                    r.setCurrentActiveTasks(u.getCurrentActiveTasks() == null ? 0 : u.getCurrentActiveTasks());
                    return r;
                })
                .collect(Collectors.toList());
    }

    /**
     * Manually assigns a specific held task to a specific user, bypassing
     * auto-routing. Useful when the marketing manager wants to hand-pick the
     * assignee rather than letting the engine pick the least-loaded user.
     */
    @Transactional
    public WorkTaskResponse assignHeldTaskToUser(String taskId, int userId, int actorId) {
        log.info("TASK manualAssign | taskId={} targetUserId={} actorId={}", taskId, userId, actorId);
        WorkTask task = workTaskRepo.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        if (task.getStatus() != TaskStatus.HELD) {
            throw new BadRequestException("Only HELD tasks can be manually assigned — this task is " + task.getStatus());
        }
        User assignee = userRepo.findById((long) userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        int updated = workTaskRepo.reassignFromHeld(taskId, userId);
        if (updated == 0) {
            throw new BadRequestException("Could not assign task — it may no longer be on hold.");
        }
        userRepo.incrementActiveTasks(assignee.getUserId());
        approvalLogRepo.insert(ApprovalLog.builder()
                .taskId(taskId).reviewerId(actorId)
                .actionTaken(ApprovalAction.UNHOLD).build());
        log.info("TASK manually assigned | taskId={} assigneeId={} assigneeName={}",
                taskId, assignee.getUserId(), assignee.getFullName());
        WorkTask updated2 = workTaskRepo.findById(taskId).orElseThrow();
        eventPublisher.publishEvent(new TaskAssignedEvent(
                taskId, assignee.getUserId().intValue(), updated2.getGranularTaskId()));
        return toWorkTaskResponse(updated2);
    }

    // -------------------------------------------------------------------------
    // Requestor Delete — task and campaign
    // -------------------------------------------------------------------------

    /**
     * Deletes a single work task from a campaign on behalf of the requestor.
     * Only the campaign owner may do this, and only for tasks that have not yet
     * been started (status ASSIGNED, HELD, or ACCEPTED).
     */
    @Transactional
    public void requestorDeleteTask(int campaignId, String taskId, int callerId, boolean isManager) {
        log.info("TASK requestorDelete | campaignId={} taskId={} callerId={} isManager={}",
                campaignId, taskId, callerId, isManager);
        Campaign campaign = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + campaignId));
        if (!isManager && campaign.getRequestorId() != callerId) {
            throw new BadRequestException("You are not the requestor of this campaign.");
        }

        WorkTask wt = workTaskRepo.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        if (wt.getCampaignId() != campaignId) {
            throw new BadRequestException("Task does not belong to this campaign.");
        }

        TaskStatus status = wt.getStatus();
        if (status == TaskStatus.IN_PROGRESS || status == TaskStatus.MANAGER_QC_REVIEW
                || status == TaskStatus.REWORK || status == TaskStatus.COMPLETED) {
            throw new BadRequestException(
                    "Cannot delete task '" + wt.getGranularTaskName()
                    + "' — it has already been started (status: " + status + ").");
        }
        // Restore assignee capacity if task was actively assigned
        if (status == TaskStatus.ASSIGNED || status == TaskStatus.ACCEPTED) {
            if (wt.getAssignedTo() != null) {
                userRepo.decrementActiveTasks(wt.getAssignedTo().longValue());
            }
        }
        workTaskRepo.deleteByTaskId(taskId);

        // Recompute campaign status: if no tasks are pending any more, mark COMPLETED.
        recomputeCampaignStatusAfterTaskChange(campaignId);
    }

    /**
     * After a task is deleted, recalculate whether the campaign should now be COMPLETED.
     * Sets COMPLETED only when every remaining task is COMPLETED/CANCELLED/REJECTED
     * AND at least one COMPLETED task still exists.
     */
    private void recomputeCampaignStatusAfterTaskChange(int campaignId) {
        int pending   = workTaskRepo.countPendingRequestorApproval(campaignId);
        int completed = workTaskRepo.countCompleted(campaignId);
        if (pending == 0 && completed > 0) {
            campaignRepo.updateStatus(campaignId, CampaignStatus.COMPLETED);
            log.info("TASK delete → all remaining tasks done — campaign COMPLETED | campaignId={}", campaignId);
        }
    }

    /**
     * Deletes an entire campaign on behalf of the requestor.  Only the campaign
     * owner may do this, and only when none of the tasks has been started.
     */
    @Transactional
    public void requestorDeleteCampaign(int campaignId, int requestorId) {
        log.info("CAMPAIGN requestorDelete | campaignId={} requestorId={}", campaignId, requestorId);
        Campaign campaign = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + campaignId));
        if (campaign.getRequestorId() != requestorId) {
            throw new BadRequestException("You are not the requestor of this campaign.");
        }
        if (workTaskRepo.hasStartedTasksForCampaign(campaignId)) {
            throw new BadRequestException(
                    "Cannot delete campaign — one or more tasks have already been started.");
        }
        // Restore capacity for any ASSIGNED/ACCEPTED tasks before deleting
        List<WorkTask> tasksToDelete = workTaskRepo.findByCampaignId(campaignId);
        List<Integer> assignedUserIds = tasksToDelete.stream()
                .filter(wt -> wt.getAssignedTo() != null)
                .map(WorkTask::getAssignedTo)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        tasksToDelete.forEach(wt -> {
            if ((wt.getStatus() == TaskStatus.ASSIGNED || wt.getStatus() == TaskStatus.ACCEPTED)
                    && wt.getAssignedTo() != null) {
                userRepo.decrementActiveTasks(wt.getAssignedTo().longValue());
            }
        });
        String campaignName = "Campaign #" + campaignId
                + (campaign.getBusinessObjective() != null ? " (" + campaign.getBusinessObjective() + ")" : "");
        campaignRepo.delete(campaignId);
        log.info("CAMPAIGN deleted | campaignId={}", campaignId);
        if (!assignedUserIds.isEmpty()) {
            eventPublisher.publishEvent(new CampaignDeletedEvent(campaignId, campaignName, assignedUserIds));
        }
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private CampaignResponse toDetailResponse(Campaign c, Integer viewerUserId) {
        int cid = c.getCampaignId();
        Map<String, List<WorkTaskQuestionnaireBriefItem>> qaByTask =
                questionnaireService.questionnaireBriefByWorkTaskForCampaign(cid);
        List<WorkTask> allTasks = workTaskRepo.findByCampaignId(cid);
        java.util.Set<String> hiddenTaskIds = shouldHideAutoCreatedChildren(c, viewerUserId)
                ? autoCreatedTaskRepo.findByCampaignId(cid).stream()
                .map(a -> a.getCreatedTaskId())
                .collect(Collectors.toSet())
                : Collections.emptySet();

        List<WorkTask> visibleTasks = allTasks.stream()
                .filter(wt -> !hiddenTaskIds.contains(wt.getTaskId()))
                .collect(Collectors.toList());

        List<WorkTaskResponse> workTasks = visibleTasks.stream()
                .map(wt -> {
                    WorkTaskResponse r = toWorkTaskResponse(wt);
                    r.setQuestionnaire(qaByTask.getOrDefault(wt.getTaskId(), Collections.emptyList()));
                    r.setActiveComments(workerCommentRepo.findActiveByTaskId(wt.getTaskId()));
                    return r;
                })
                .collect(Collectors.toList());
        workTasks = autoCreatedTaskService.enrichAll(workTasks);
        // Build deliverables from non-cancelled visible work tasks
        List<DeliverableResponse> deliverables = visibleTasks.stream()
                .filter(wt -> wt.getStatus() != TaskStatus.CANCELLED)
                .map(wt -> DeliverableResponse.builder()
                        .taskId(wt.getTaskId())
                        .granularTaskId(wt.getGranularTaskId())
                        .granularTaskName(wt.getGranularTaskName())
                        .workTaskStatus(wt.getStatus() == null ? null : wt.getStatus().name())
                        .build())
                .collect(Collectors.toList());
        // Campaign-level files (work_task_id IS NULL)
        List<String> fileUrls        = campaignRepo.findFileUrlsByCampaignId(cid);
        List<String> fileOriginalNames = campaignRepo.findFileNamesByCampaignId(cid);

        // Task-specific files — hydrate onto each WorkTaskResponse
        java.util.Map<String, java.util.List<String[]>> taskFilesMap =
                campaignRepo.findTaskFilesByCampaignId(cid);
        workTasks.forEach(t -> {
            java.util.List<String[]> rows = taskFilesMap.getOrDefault(t.getTaskId(), java.util.List.of());
            t.setFileUrls(rows.stream().map(r -> r[0]).collect(Collectors.toList()));
            t.setFileOriginalNames(rows.stream().map(r -> r[1]).collect(Collectors.toList()));
        });

        CampaignResponse resp = toSummaryResponse(c);
        resp.setDeliverables(deliverables);
        resp.setWorkTasks(workTasks);
        resp.setFileUrls(fileUrls);
        resp.setFileOriginalNames(fileOriginalNames);
        return resp;
    }

    private boolean shouldHideAutoCreatedChildren(Campaign campaign, Integer viewerUserId) {
        if (viewerUserId == null || campaign.getRequestorId() == null
                || campaign.getRequestorId().intValue() != viewerUserId) {
            return false;
        }
        User viewer = userRepo.findById((long) viewerUserId).orElse(null);
        if (viewer == null) return false;
        return !viewer.hasRole("Marketing Manager") && !viewer.hasRole("Admin");
    }

    CampaignResponse toSummaryResponse(Campaign c) {
        // Resolve multi-select JSON arrays to comma-separated display names.
        String audienceNames = masterDataService.resolveIdListToNames(
                c.getAudienceTypeId(), MasterTableType.AUDIENCES);
        String languageNames = masterDataService.resolveIdListToNames(
                c.getLanguage(), MasterTableType.LANGUAGES);
        String toneNames     = masterDataService.resolveIdListToNames(
                c.getTone(), MasterTableType.TONES);
        String vendorNames   = masterDataService.resolveIdListToNames(
                c.getVendorType(), MasterTableType.VENDOR_TYPES);

        return CampaignResponse.builder()
                .campaignId(c.getCampaignId())
                .requestorId(c.getRequestorId())
                .requestorName(c.getRequestorName())
                .departmentId(c.getDepartmentId())
                .departmentName(c.getDepartmentName())
                .targetLocation(c.getTargetLocation())
                .businessObjectiveId(c.getBusinessObjectiveId())
                .businessObjective(c.getBusinessObjective())
                .campaignTypeId(c.getCampaignTypeId())
                .businessVerticalId(c.getBusinessVerticalId())
                .businessTypeId(c.getBusinessTypeId())
                .storeFormatTypeId(c.getStoreFormatTypeId())
                .storeId(c.getStoreId())
                .contactNumber(c.getContactNumber())
                // Raw JSON arrays (for form pre-population on edit)
                .audienceTypeId(c.getAudienceTypeId())
                .languageIds(c.getLanguage())
                .toneIds(c.getTone())
                .vendorTypeIds(c.getVendorType())
                // Resolved display names (for brief drawer display)
                .audienceName(audienceNames)
                .language(languageNames)
                .hasOffer(c.getHasOffer())
                .offerTypeId(c.getOfferTypeId())
                .offerTypeName(c.getOfferTypeName())
                .keyMessage(c.getKeyMessage())
                .supportingProofId(c.getSupportingProofId())
                .supportingProof(c.getSupportingProof())
                .tone(toneNames)
                .priority(c.getPriority() == null ? null : c.getPriority().name())
                .budgetTierId(c.getBudgetTierId())
                .budgetTier(c.getBudgetTier())
                .vendorRequired(c.getVendorRequired())
                .vendorType(vendorNames)
                .kpiTypeId(c.getKpiTypeId())
                .kpiType(c.getKpiType())
                .expectedOutputId(c.getExpectedOutputId())
                .expectedOutput(c.getExpectedOutput())
                .status(c.getStatus() == null ? null : c.getStatus().name())
                .routingNotes(c.getRoutingNotes())
                .flaggedInconsistency(c.getFlaggedInconsistency())
                .inconsistencyReason(c.getInconsistencyReason())
                .rejectionReason(c.getRejectionReason())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .taskCount(c.getTaskCount())
                .completedTaskCount(c.getCompletedTaskCount())
                .hasRework(c.getHasRework())
                .hasQcReview(c.getHasQcReview())
                .hasUnansweredComments(c.getHasUnansweredComments())
                .deliverables(Collections.emptyList())
                .workTasks(Collections.emptyList())
                .build();
    }

    static WorkTaskResponse toWorkTaskResponse(WorkTask wt) {
        return WorkTaskResponse.builder()
                .taskId(wt.getTaskId())
                .campaignId(wt.getCampaignId())
                .requestorName(wt.getRequestorName())
                .latestActionDoneByName(wt.getLatestActionDoneByName())
                .requestorId(wt.getRequestorId())
                .granularTaskId(wt.getGranularTaskId())
                .granularTaskName(wt.getGranularTaskName())
                .taskTypeName(wt.getTaskTypeName())
                .assignedTo(wt.getAssignedTo())
                .assigneeName(wt.getAssigneeName())
                .status(wt.getStatus() == null ? null : wt.getStatus().name())
                .campaignDeadline(wt.getCampaignDeadline())
                .campaignPriority(wt.getCampaignPriority() == null ? null : wt.getCampaignPriority().name())
                .campaignStatus(wt.getCampaignStatus() == null ? null : wt.getCampaignStatus().name())
                .assignedAt(wt.getAssignedAt())
                .acceptedAt(wt.getAcceptedAt())
                .startedAt(wt.getStartedAt())
                .submittedAt(wt.getSubmittedAt())
                .managerApprovedAt(wt.getManagerApprovedAt())
                .requestorApprovedAt(wt.getRequestorApprovedAt())
                .totalTimeLoggedMinutes(wt.getTotalTimeLoggedMinutes())
                .dynamicDeadline(wt.getDynamicDeadline())
                .submissionNotes(wt.getSubmissionNotes())
                .createdAt(wt.getCreatedAt())
                .reworkCount(wt.getReworkCount())
                .requestorReworkCount(wt.getRequestorReworkCount())
                .latestManagerReworkComment(wt.getLatestManagerReworkComment())
                .latestRequestorReworkComment(wt.getLatestRequestorReworkComment())
                .latestReworkComment(wt.getLatestReworkComment())
                .latestReworkSource(wt.getLatestReworkSource())
                .collaborationStarted(wt.isCollaborationStarted())
                .collaborationActive(wt.isCollaborationActive())
                .storeId(wt.getStoreId())
                .contactNumber(wt.getContactNumber())
                .hasActiveComments(wt.isHasActiveComments())
                .build();
    }

}
