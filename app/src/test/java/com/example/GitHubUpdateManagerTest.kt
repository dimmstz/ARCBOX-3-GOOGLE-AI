package com.example

import com.example.data.update.GitHubUpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateManagerTest {

    @Test
    fun testSameVersionNeverTriggersUpdate() {
        // Cenário do bug reportado: usuário está na v1.0.6 e a release é v1.0.6
        val isNewer = GitHubUpdateManager.isRemoteVersionNewer(
            installedVersionCode = 7,
            installedVersionName = "1.0.6",
            remoteExplicitCode = null,
            remoteVersionName = "1.0.6"
        )
        assertFalse("Mesma versão (1.0.6 == 1.0.6) NÃO deve disparar atualização", isNewer)

        val isNewerWithV = GitHubUpdateManager.isRemoteVersionNewer(
            installedVersionCode = 7,
            installedVersionName = "v1.0.6",
            remoteExplicitCode = null,
            remoteVersionName = "v1.0.6"
        )
        assertFalse("Mesma versão com prefixo 'v' NÃO deve disparar atualização", isNewerWithV)
    }

    @Test
    fun testNewerVersionTriggersUpdate() {
        val isNewer = GitHubUpdateManager.isRemoteVersionNewer(
            installedVersionCode = 7,
            installedVersionName = "1.0.6",
            remoteExplicitCode = null,
            remoteVersionName = "1.0.7"
        )
        assertTrue("Versão remota 1.0.7 é mais recente que 1.0.6", isNewer)

        val isNewerMajor = GitHubUpdateManager.isRemoteVersionNewer(
            installedVersionCode = 7,
            installedVersionName = "1.0.6",
            remoteExplicitCode = null,
            remoteVersionName = "2.0.0"
        )
        assertTrue("Versão remota 2.0.0 é mais recente que 1.0.6", isNewerMajor)
    }

    @Test
    fun testOlderVersionNeverTriggersUpdate() {
        val isOlder = GitHubUpdateManager.isRemoteVersionNewer(
            installedVersionCode = 8,
            installedVersionName = "1.0.7",
            remoteExplicitCode = null,
            remoteVersionName = "1.0.6"
        )
        assertFalse("Versão remota 1.0.6 NÃO é mais recente que a instalada 1.0.7", isOlder)

        val isOlderMinor = GitHubUpdateManager.isRemoteVersionNewer(
            installedVersionCode = 8,
            installedVersionName = "1.1.0",
            remoteExplicitCode = null,
            remoteVersionName = "1.0.9"
        )
        assertFalse("Versão remota 1.0.9 NÃO é mais recente que a instalada 1.1.0", isOlderMinor)
    }

    @Test
    fun testExplicitBuildCodeComparison() {
        val codeExtracted = GitHubUpdateManager.extractExplicitVersionCode("v1.0.7+8", "ArcBox v1.0.7")
        assertEquals(8, codeExtracted)

        // Se o nome de versão for o mesmo, mas o build code for maior
        val isNewerBuild = GitHubUpdateManager.isRemoteVersionNewer(
            installedVersionCode = 7,
            installedVersionName = "1.0.7",
            remoteExplicitCode = 8,
            remoteVersionName = "1.0.7"
        )
        assertTrue("Build 8 é superior ao build 7 na mesma versão 1.0.7", isNewerBuild)

        // Se o build code for igual ou menor
        val isSameBuild = GitHubUpdateManager.isRemoteVersionNewer(
            installedVersionCode = 8,
            installedVersionName = "1.0.7",
            remoteExplicitCode = 8,
            remoteVersionName = "1.0.7"
        )
        assertFalse("Mesmo build code (8 == 8) NÃO deve disparar atualização", isSameBuild)
    }

    @Test
    fun testCleanVersionName() {
        assertEquals("1.0.6", GitHubUpdateManager.cleanVersionName("v1.0.6"))
        assertEquals("1.0.6", GitHubUpdateManager.cleanVersionName("V1.0.6"))
        assertEquals("1.0.6", GitHubUpdateManager.cleanVersionName("1.0.6"))
    }
}
