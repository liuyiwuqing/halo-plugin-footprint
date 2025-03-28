package cc.lik.footprint;

import cc.lik.footprint.model.Footprint;
import cc.lik.footprint.dto.BaseConfig;
import cc.lik.footprint.service.FootprintService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.theme.TemplateNameResolver;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.app.extension.ReactiveExtensionClient;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@Slf4j
@Configuration
@AllArgsConstructor
public class FootprintRouter {

    private final TemplateNameResolver templateNameResolver;
    private final ReactiveExtensionClient client;
    private final FootprintService footprintSvc;

    @Bean
    RouterFunction<ServerResponse> footprintRouterFunction() {
        return route(GET("/footprints"), this::renderFootprintPage);
    }

    private Mono<ServerResponse> renderFootprintPage(ServerRequest request) {
        log.info("开始渲染足迹页面");
        return footprintSvc.getConfigByGroupName()
            .flatMap(settings -> {
                log.info("获取到配置信息: {}", settings);
                // 按创建时间倒序排序
                Comparator<Footprint> comparator = 
                    (f1, f2) -> f2.getSpec().getCreateTime()
                        .compareTo(f1.getSpec().getCreateTime());

                return client.list(Footprint.class,
                        null,
                        comparator,
                        0,
                        1000) // 获取所有足迹
                    .flatMap(footprints -> {
                        log.info("获取到足迹数据: {} 条", footprints.getItems().size());
                        Map<String, Object> model = new HashMap<>();
                        model.put("settings", settings);
                        model.put("footprints", footprints.getItems());

                        return templateNameResolver.resolveTemplateNameOrDefault(
                                request.exchange(), 
                                "footprint"
                            )
                            .flatMap(templateName -> {
                                log.info("使用模板: {}", templateName);
                                return ServerResponse.ok()
                                    .render(templateName, model);
                            });
                    });
            })
            .onErrorResume(e -> {
                log.error("渲染足迹页面失败", e);
                return ServerResponse.status(INTERNAL_SERVER_ERROR)
                    .bodyValue("渲染足迹页面失败: " + e.getMessage());
            });
    }
} 