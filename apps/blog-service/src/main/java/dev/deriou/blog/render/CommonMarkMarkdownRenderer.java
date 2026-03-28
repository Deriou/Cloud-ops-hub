package dev.deriou.blog.render;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

@Component
public class CommonMarkMarkdownRenderer implements MarkdownRenderer {

    private final Parser parser;
    private final HtmlRenderer htmlRenderer;

    public CommonMarkMarkdownRenderer() {
        this.parser = Parser.builder().build();
        this.htmlRenderer = HtmlRenderer.builder().build();
    }

    @Override
    public String render(String markdown) {
        return htmlRenderer.render(parser.parse(markdown));
    }
}
