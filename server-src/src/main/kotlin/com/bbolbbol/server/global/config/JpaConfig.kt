package com.bbolbbol.server.global.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

/** BaseTimeEntity의 createdAt / updatedAt 자동 기록 */
@Configuration
@EnableJpaAuditing
class JpaConfig
