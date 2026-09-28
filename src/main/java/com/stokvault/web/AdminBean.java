package com.stokvault.web;

import com.stokvault.domain.ContributionFrequency;
import com.stokvault.domain.GroupType;
import com.stokvault.domain.NotificationChannel;
import com.stokvault.domain.NotificationStatus;
import com.stokvault.dto.GroupRequest;
import com.stokvault.dto.MemberRegistration;
import com.stokvault.dto.MemberView;
import com.stokvault.dto.NotificationView;
import com.stokvault.dto.PlatformReport;
import com.stokvault.entity.StokvelGroup;
import com.stokvault.service.AdminService;
import com.stokvault.service.GroupService;
import com.stokvault.service.MemberService;
import com.stokvault.service.NotificationLogService;
import com.stokvault.service.ReportService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Coop Office administration (SDD actor "Coop Office Admin"): platform-wide report, registering
 * groups and people, the notification log, and running the background jobs on demand.
 */
@Named
@ViewScoped
public class AdminBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject private ReportService reports;
    @Inject private GroupService groups;
    @Inject private MemberService members;
    @Inject private NotificationLogService notificationLog;
    @Inject private AdminService admin;
    @Inject private UserSession user;

    private PlatformReport report;
    private List<NotificationView> notifications = List.of();
    private List<MemberView> people = List.of();
    private NotificationStatus notificationFilter;
    private String peopleSearch;

    // New group form
    private String name;
    private String description;
    private GroupType type = GroupType.ROTATIONAL;
    private BigDecimal contributionAmount;
    private ContributionFrequency frequency = ContributionFrequency.MONTHLY;
    private LocalDate startDate = LocalDate.now().withDayOfMonth(1).plusMonths(1);
    private BigDecimal approvalThreshold = new BigDecimal("5000");
    private Integer completionThreshold = 100;
    private BigDecimal benefitAmount;

    // Register person form
    private String regName;
    private String regNationalId;
    private String regPhone;
    private String regEmail;
    private NotificationChannel regChannel = NotificationChannel.SMS;
    private boolean regConsent;

    @PostConstruct
    void load() {
        if (!user.isAdmin()) {
            return;
        }
        report = reports.platform();
        notifications = notificationLog.all(notificationFilter).stream().map(NotificationView::from).toList();
        people = members.search(peopleSearch).stream().map(MemberView::from).toList();
    }

    public void createGroup() throws IOException {
        StokvelGroup[] created = new StokvelGroup[1];
        boolean ok = Ui.attempt(() -> created[0] = groups.create(new GroupRequest(name, description, type, contributionAmount,
                frequency, startDate, approvalThreshold, completionThreshold, benefitAmount)),
                "Group created as a draft. Add members and appoint a treasurer, then activate it.");
        if (ok) {
            FacesContext.getCurrentInstance().getExternalContext()
                    .redirect("group.xhtml?id=" + created[0].getId() + "&tab=members");
        }
    }

    public void register() {
        if (Ui.attempt(() -> members.register(new MemberRegistration(regName, regNationalId, regPhone, regEmail, regChannel, regConsent)),
                "Registered. They sign in the first time with an SMS code, then choose a password.")) {
            regName = null;
            regNationalId = null;
            regPhone = null;
            regEmail = null;
            regConsent = false;
        }
        load();
    }

    public void filter() {
        load();
    }

    public void runEligibility() {
        Ui.attempt(() -> Ui.info("Eligibility check started for all groups (job " + admin.startEligibilityCheckForAllGroups() + ")"), null);
    }

    public void sendReminders() {
        Ui.attempt(() -> Ui.info(admin.sendRemindersNow() + " reminder(s) queued"), null);
        load();
    }

    public NotificationStatus[] getNotificationStatuses() {
        return NotificationStatus.values();
    }

    public GroupType[] getTypes() {
        return GroupType.values();
    }

    public ContributionFrequency[] getFrequencies() {
        return ContributionFrequency.values();
    }

    public NotificationChannel[] getChannels() {
        return new NotificationChannel[]{NotificationChannel.SMS, NotificationChannel.WHATSAPP};
    }

    public PlatformReport getReport() { return report; }
    public List<NotificationView> getNotifications() { return notifications; }
    public List<MemberView> getPeople() { return people; }
    public NotificationStatus getNotificationFilter() { return notificationFilter; }
    public void setNotificationFilter(NotificationStatus notificationFilter) { this.notificationFilter = notificationFilter; }
    public String getPeopleSearch() { return peopleSearch; }
    public void setPeopleSearch(String peopleSearch) { this.peopleSearch = peopleSearch; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public GroupType getType() { return type; }
    public void setType(GroupType type) { this.type = type; }
    public BigDecimal getContributionAmount() { return contributionAmount; }
    public void setContributionAmount(BigDecimal contributionAmount) { this.contributionAmount = contributionAmount; }
    public ContributionFrequency getFrequency() { return frequency; }
    public void setFrequency(ContributionFrequency frequency) { this.frequency = frequency; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public BigDecimal getApprovalThreshold() { return approvalThreshold; }
    public void setApprovalThreshold(BigDecimal approvalThreshold) { this.approvalThreshold = approvalThreshold; }
    public Integer getCompletionThreshold() { return completionThreshold; }
    public void setCompletionThreshold(Integer completionThreshold) { this.completionThreshold = completionThreshold; }
    public BigDecimal getBenefitAmount() { return benefitAmount; }
    public void setBenefitAmount(BigDecimal benefitAmount) { this.benefitAmount = benefitAmount; }
    public String getRegName() { return regName; }
    public void setRegName(String regName) { this.regName = regName; }
    public String getRegNationalId() { return regNationalId; }
    public void setRegNationalId(String regNationalId) { this.regNationalId = regNationalId; }
    public String getRegPhone() { return regPhone; }
    public void setRegPhone(String regPhone) { this.regPhone = regPhone; }
    public String getRegEmail() { return regEmail; }
    public void setRegEmail(String regEmail) { this.regEmail = regEmail; }
    public NotificationChannel getRegChannel() { return regChannel; }
    public void setRegChannel(NotificationChannel regChannel) { this.regChannel = regChannel; }
    public boolean isRegConsent() { return regConsent; }
    public void setRegConsent(boolean regConsent) { this.regConsent = regConsent; }
}
