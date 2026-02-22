package io.kotest.provided

import com.example.IntegrationTestBase
import io.kotest.core.config.AbstractProjectConfig

class ProjectConfig : AbstractProjectConfig() {
    override suspend fun beforeProject() {
        IntegrationTestBase.postgres.start()
        IntegrationTestBase.dbmate.start()
        IntegrationTestBase.valkey.start()
    }

    override suspend fun afterProject() {
        IntegrationTestBase.valkey.stop()
        IntegrationTestBase.dbmate.stop()
        IntegrationTestBase.postgres.stop()
    }
}
