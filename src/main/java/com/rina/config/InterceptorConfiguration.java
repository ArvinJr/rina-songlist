package com.rina.config;

import com.rina.interceptor.AuthInfoInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 自定义拦截器的配置文件
 *
 * @author arvin
 * @date 2022/02/09
 */
@Configuration
@RequiredArgsConstructor
public class InterceptorConfiguration implements WebMvcConfigurer {

	private final AuthInfoInterceptor authInfoInterceptor;

	/**
	 * 权限拦截配置
	 * @param registry
	 */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(authInfoInterceptor)
				.addPathPatterns("/private/system/**");
//				.excludePathPatterns("/private/login");
	}

	/**
	 * 跨域配置
	 */
	@Bean
	public CorsFilter corsFilter()
	{
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowCredentials(true);
		// 设置访问源地址
		config.addAllowedOriginPattern("*");
		// 设置访问源请求头
		config.addAllowedHeader("*");
		// 设置访问源请求方法
		config.addAllowedMethod("*");
		config.addExposedHeader("Authorization");
		// 有效期 1800秒
		config.setMaxAge(3600L);
		// 添加映射路径，拦截一切请求
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		// 返回新的CorsFilter
		return new CorsFilter(source);
	}

}
