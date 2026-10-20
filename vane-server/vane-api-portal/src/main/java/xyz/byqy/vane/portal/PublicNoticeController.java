package xyz.byqy.vane.portal;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import xyz.byqy.vane.content.application.NoticeService;
import xyz.byqy.vane.content.application.PageResult;
import xyz.byqy.vane.content.application.PublicNoticeView;

/** 门户公告（PTL-02）：匿名可读，按 Accept-Language 返回对应语言。 */
@RestController
@RequestMapping("/api/v1/public/notices")
public class PublicNoticeController {

    private final NoticeService notices;

    public PublicNoticeController(NoticeService notices) {
        this.notices = notices;
    }

    @GetMapping
    public PageResult<PublicNoticeView> list(
            @RequestParam(name = "page[number]", defaultValue = "0") int pageNumber,
            @RequestParam(name = "page[size]", defaultValue = "20") int pageSize) {
        return notices.publicPage(LocaleContextHolder.getLocale().toLanguageTag(), pageNumber, pageSize);
    }
}
