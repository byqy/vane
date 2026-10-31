-- 服务角色（ADR-030 P1）：区分「给服务账号/Agent 用的最小权限角色」与普通业务角色。
-- 服务角色不出现在人工用户的可指派角色列表里，权限码仍取自 PermissionCatalog 既有码。
alter table iam_role add column service boolean not null default false;

comment on column iam_role.service is '是否服务角色（供服务账号/Agent 使用，不指派给人工用户）';