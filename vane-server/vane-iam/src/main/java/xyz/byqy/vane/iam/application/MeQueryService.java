package xyz.byqy.vane.iam.application;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.i18n.MessageResolver;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.iam.domain.IamRoleRepository;
import xyz.byqy.vane.iam.domain.IamUser;
import xyz.byqy.vane.iam.domain.IamUserRepository;
import xyz.byqy.vane.iam.domain.PermissionCatalog;

/** 组装当前用户信息：账号、角色、权限码与可见菜单（前端动态路由的数据源）。 */
@Service
public class MeQueryService {

    private final IamUserRepository users;
    private final IamRoleRepository roles;
    private final RoleAssignmentRepository assignments;
    private final MessageResolver messages;

    public MeQueryService(IamUserRepository users, IamRoleRepository roles, RoleAssignmentRepository assignments,
                          MessageResolver messages) {
        this.users = users;
        this.roles = roles;
        this.assignments = assignments;
        this.messages = messages;
    }

    @Transactional(readOnly = true)
    public MeView load(UUID userId) {
        IamUser user = users.findById(userId)
                .filter(IamUser::isActive)
                .orElseThrow(() -> new ApiException(CommonErrors.AUTH_TOKEN_INVALID));

        List<String> roleCodes = roles.findRoleCodesByUserId(userId);
        List<String> permissionCodes = assignments.permissionCodesOfUser(userId);
        List<MeView.MenuView> menus = PermissionCatalog.menus().stream()
                .filter(permission -> permission.isVisible() && permissionCodes.contains(permission.getCode()))
                .sorted(Comparator.comparingInt(PermissionCatalog::getSortNo)
                        .thenComparing(PermissionCatalog::getCode))
                .map(permission -> new MeView.MenuView(permission.getCode(), permission.getParentCode(),
                        permission.getNameKey(), permission.getPath(), permission.getComponent(),
                        permission.getIcon(), permission.getSortNo()))
                .toList();

        return new MeView(user.getId(), user.getUsername(), user.getDeptId(), user.isMustChangePassword(),
                roleCodes, permissionCodes, menus, user.getLocale(), user.getTimezone());
    }

    /**
     * 保存用户偏好：语言（BCP-47，必须在支持清单内）与时区（IANA ID）。
     * 至少提供一个字段；只更新非空字段。
     */
    @Transactional
    public void updatePreferences(UUID userId, String localeTag, String timezone) {
        boolean hasLocale = localeTag != null && !localeTag.isBlank();
        boolean hasTimezone = timezone != null && !timezone.isBlank();
        if (!hasLocale && !hasTimezone) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        String normalizedLocale = null;
        if (hasLocale) {
            if (!messages.isSupportedLocale(localeTag)) {
                throw new ApiException(CommonErrors.VALIDATION_FAILED);
            }
            normalizedLocale = messages.normalizeLocale(localeTag);
        }
        String normalizedTimezone = null;
        if (hasTimezone) {
            normalizedTimezone = normalizeTimezone(timezone);
        }

        IamUser user = users.findById(userId)
                .filter(IamUser::isActive)
                .orElseThrow(() -> new ApiException(CommonErrors.AUTH_TOKEN_INVALID));
        if (normalizedLocale != null) {
            user.changeLocale(normalizedLocale);
        }
        if (normalizedTimezone != null) {
            user.changeTimezone(normalizedTimezone);
        }
        users.save(user);
    }

    private String normalizeTimezone(String timezone) {
        String trimmed = timezone.strip();
        if (trimmed.length() > 64) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        try {
            // 只接受 IANA 时区 ID（如 Asia/Shanghai），拒绝固定偏移与随意字符串
            return ZoneId.of(trimmed).getId();
        } catch (DateTimeException ex) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
    }
}
