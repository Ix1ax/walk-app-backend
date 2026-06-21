package dev.walk.backend.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * @author Ilya Samsonov
 * Кэш в памяти (Caffeine). Пока единственный кэш — {@code placeMedia}: фото и
 * описание места из Wikipedia, чтобы не дёргать внешний API на каждой генерации.
 * Кэшируем и «пусто» (у места нет статьи) — тоже не дёргать повторно. На 2+
 * инстансах backend стоит вынести в Redis/БД, сейчас преждевременно
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String PLACE_MEDIA = "placeMedia";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(PLACE_MEDIA);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofDays(7)));
        return manager;
    }
}
