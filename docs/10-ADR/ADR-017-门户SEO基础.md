# ADR-017：门户 SEO 基础

| 项 | 值 |
|---|---|
| 状态 | 已接受 |
| 日期 | 2026-10-19 |
| 影响文档 | 02 §5、03 §16、04 §10、07 §10、08 §14 |
| 取代 | 部分取代 [ADR-006](ADR-006-门户纯SPA.md) 中「门户不需要 SEO」的结论 |

## 背景

目标客户是需要持续发布内容的机构：门户要能被搜索引擎收录，并支持多语言内容各自可被索引。
ADR-006 确定门户为纯 SPA，其结论是「门户不需要 SEO」。内容运营需求出现后，该结论不再成立，
但**渲染模式保持在 SPA**，不引入 SSR 或构建期预渲染。

## 决策

1. **URL 携带语言**：门户路由为 `/{locale}` 与 `/{locale}/{slug}`，语言是 URL 的一部分，
   便于搜索引擎区分不同语言版本并建立 `hreflang` 关系。旧式 `/{slug}` 链接重定向到当前语言。
2. **动态页面元数据**：标题、描述、canonical、Open Graph、`hreflang`、`og:type` 由页面在数据加载后写入
   `document.head`（`@vane/shared` 的 `applySeo`）。
3. **服务端可爬取清单**：`sitemap.xml` 与 `robots.txt` 由后端生成，内容来自数据库（已发布内容页 × 全部语言），
   不依赖前端渲染结果；`robots.txt` 禁止抓取 `/admin/`。
4. **站点级 SEO 配置入系统参数**：`seo.site-name`、`seo.description`、`seo.base-url`、`seo.og-image`，
   部署时必须把 `seo.base-url` 改成对外域名。
5. **暂不引入**：SSR / 构建期预渲染 / 静态生成；若后续要求更好的首屏收录，再以 ADR 评估预渲染。

## 影响

| 项 | 影响 |
|---|---|
| 门户路由 | 增加语言前缀；`LanguageSelect` 切换即导航，保留当前页面 |
| 后端 | 新增 `/api/v1/public/site`、`/api/v1/public/pages`、`/sitemap.xml`、`/robots.txt`（放行匿名访问） |
| 部署 | Nginx 需把 `/sitemap.xml`、`/robots.txt` 反代到应用 |
| 文档 | 04 恢复 SEO 本地化章节；08 增加门户 SEO 约定 |

## 风险

- 纯 SPA 下不执行 JS 的爬虫只能读到 `index.html` 的静态兜底元数据；Google/Bing 等执行 JS 的引擎可读取动态元数据。
- `sitemap.xml` 按小时缓存，内容发布后最长 1 小时才出现在新抓取中；可接受，必要时缩短缓存。
