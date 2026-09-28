package com.stokvault.web;

import com.stokvault.domain.NotificationChannel;
import com.stokvault.dto.AuthRequests;
import com.stokvault.dto.MemberUpdate;
import com.stokvault.dto.MemberView;
import com.stokvault.dto.NotificationView;
import com.stokvault.service.AuthService;
import com.stokvault.service.MemberService;
import com.stokvault.service.NotificationLogService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * My account: password, contact details and the notifications sent to me.
 */
@Named
@ViewScoped
public class AccountBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject private AuthService auth;
    @Inject private MemberService members;
    @Inject private NotificationLogService notificationLog;
    @Inject private UserSession user;

    private String currentPassword;
    private String newPassword;
    private String confirmPassword;

    private String fullName;
    private String phoneNumber;
    private String email;
    private NotificationChannel preferredChannel;

    private List<NotificationView> notifications = List.of();

    @PostConstruct
    void load() {
        MemberView me = MemberView.from(members.me());
        fullName = me.fullName();
        phoneNumber = me.phoneNumber();
        email = me.email();
        preferredChannel = me.preferredChannel();
        notifications = notificationLog.mine().stream().map(NotificationView::from).toList();
    }

    public boolean isPasswordSet() {
        return user.getMe().passwordSet();
    }

    public void changePassword() {
        if (!Objects.equals(newPassword, confirmPassword)) {
            Ui.error("The two new passwords don't match");
            return;
        }
        if (Ui.attempt(() -> auth.changePassword(new AuthRequests.PasswordChange(currentPassword, newPassword)),
                "Password saved. Use it with your phone number next time you log in.")) {
            user.refresh();
            currentPassword = null;
            newPassword = null;
            confirmPassword = null;
        }
    }

    public void saveDetails() {
        if (Ui.attempt(() -> members.update(user.getMe().id(), new MemberUpdate(fullName, phoneNumber, email, preferredChannel)),
                "Details saved")) {
            user.refresh();
        }
    }

    public NotificationChannel[] getChannels() {
        return NotificationChannel.values();
    }

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public NotificationChannel getPreferredChannel() { return preferredChannel; }
    public void setPreferredChannel(NotificationChannel preferredChannel) { this.preferredChannel = preferredChannel; }
    public List<NotificationView> getNotifications() { return notifications; }
}
