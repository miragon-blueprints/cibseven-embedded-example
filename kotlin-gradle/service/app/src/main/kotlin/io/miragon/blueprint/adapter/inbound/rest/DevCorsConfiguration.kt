package io.miragon.blueprint.adapter.inbound.rest

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Documented escape hatch you should NOT need. In development the Vite dev server proxies `/api`,
 * `/v3/api-docs`, `/engine-rest` and `/camunda` to the backend, so the browser sees a single origin
 * and there is no CORS on the production path. This bean only activates under the `dev` profile — if
 * you ever run the frontend against the backend cross-origin, enable it. See CONTRIBUTING.md.
 */
@Configuration
@Profile("dev")
class DevCorsConfiguration : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry
            .addMapping("/api/**")
            .allowedOrigins("http://localhost:5173")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH")
    }
}
