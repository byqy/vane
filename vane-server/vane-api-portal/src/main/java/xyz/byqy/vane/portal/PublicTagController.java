package xyz.byqy.vane.portal;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import xyz.byqy.vane.content.application.PublicTagView;
import xyz.byqy.vane.content.application.TagService;

/** 门户文章标签（PTL-02）：带已发布文章计数。 */
@RestController
@RequestMapping("/api/v1/public/tags")
public class PublicTagController {

    private final TagService tags;

    public PublicTagController(TagService tags) {
        this.tags = tags;
    }

    @GetMapping
    public List<PublicTagView> list() {
        return tags.publicList();
    }
}
