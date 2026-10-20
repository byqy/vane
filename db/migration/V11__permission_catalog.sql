-- V11: 权限点代码化（ADR-021）。
-- 权限点定义移入代码（PermissionCatalog），数据库只保留角色与权限码的授权关系。

ALTER TABLE iam_role_permission ADD COLUMN permission_code text;

UPDATE iam_role_permission rp
   SET permission_code = p.code
  FROM iam_permission p
 WHERE p.id = rp.permission_id;

DELETE FROM iam_role_permission WHERE permission_code IS NULL;

ALTER TABLE iam_role_permission ALTER COLUMN permission_code SET NOT NULL;

ALTER TABLE iam_role_permission DROP CONSTRAINT iam_role_permission_pkey;
ALTER TABLE iam_role_permission DROP CONSTRAINT iam_role_permission_permission_id_fkey;
ALTER TABLE iam_role_permission DROP COLUMN permission_id;
ALTER TABLE iam_role_permission ADD CONSTRAINT pk_iam_role_permission PRIMARY KEY (role_id, permission_code);

CREATE INDEX idx_iam_role_permission_code ON iam_role_permission (permission_code);

DROP TABLE iam_permission;

COMMENT ON TABLE iam_role_permission IS '角色-权限授权关系；权限点定义见代码 PermissionCatalog（ADR-021）';
