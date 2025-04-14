/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.system


import org.springframework.boot.autoconfigure.cache.JCacheManagerCustomizer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import javax.cache.configuration.Configuration
import javax.cache.configuration.MutableConfiguration

/**
 * Cache configuration intended for caches providing the JCache API. This configuration creates the used cache for the
 * application and enables statistics that become accessible via JMX.
 *
 * @author Antoine Rey
 * @author Stephane Nicoll
 * @author Jens Wilke
 */
@org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
@EnableCaching
class CacheConfig {

    @Bean
    fun cacheManagerCustomizer(): JCacheManagerCustomizer {
        return JCacheManagerCustomizer {
            it.createCache("vets", createCacheConfiguration())
        }
    }

    /**
     * Create a simple configuration that enable statistics via the JCache programmatic configuration API.
     * <p>
     * Within the configuration object that is provided by the JCache API standard, there is only a very limited set of
     * configuration options. The really relevant configuration options (like the size limit) must be set via a
     * configuration mechanism that is provided by the selected JCache implementation.
     */
    private fun createCacheConfiguration(): Configuration<Any, Any> =
            MutableConfiguration<Any, Any>().setStatisticsEnabled(true)
}
