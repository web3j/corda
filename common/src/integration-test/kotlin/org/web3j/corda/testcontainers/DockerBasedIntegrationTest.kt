/*
 * Copyright 2019 Web3 Labs LTD.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.web3j.corda.testcontainers

import com.samskivert.mustache.Mustache
import io.bluebank.braid.server.Braid
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.containers.BindMode.READ_WRITE
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.web3j.corda.model.LoginRequest
import org.web3j.corda.model.NotaryType
import org.web3j.corda.protocol.CordaService
import org.web3j.corda.protocol.NetworkMap
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.CountDownLatch

@Testcontainers
open class DockerBasedIntegrationTest {

    @Test
    fun `test to setup docker containers`() {
        val notary = createNodeContainer("Notary", "London", "GB", 10005, 10006, 10007, true)
        notary.start()

        val partyA = createNodeContainer("PartyA", "Tokyo", "JP", 10008, 10009, 10010, false)
        partyA.start()

        val partyB = createNodeContainer("PartyB", "New York", "US", 10011, 10012, 10013, false)
        partyB.start()

        Braid().withPort(9000)
            .withUserName("user1")
            .withPassword("test")
            .withNodeAddress("localhost:${partyA.getMappedPort(10009)}")
            .startServer()

        CountDownLatch(1).await()
        notary.stop()
        partyA.stop()
        partyB.stop()
    }

    companion object {

        private val PREFIX = if (System.getProperty("os.name").contains("Mac", true)) {
            "/private"
        } else {
            ""
        }

        private const val NETWORK_MAP_ALIAS = "networkmap"
        private const val NETWORK_MAP_URL = "http://${NETWORK_MAP_ALIAS}:8080"

        private const val NETWORK_MAP_IMAGE = "cordite/network-map:v0.4.5"
        private const val CORDA_ZULU_IMAGE = "corda/corda-zulu-4.1:latest"

        @TempDir
        private lateinit var nodes: File

        private val network: Network = Network.newNetwork()
        private val timeOut: Duration = Duration.ofMinutes(2)

        @JvmStatic
        @Container
        val NETWORK_MAP: KGenericContainer = KGenericContainer(NETWORK_MAP_IMAGE)
            .withCreateContainerCmdModifier {
                it.withHostName(NETWORK_MAP_ALIAS)
                it.withName(NETWORK_MAP_ALIAS)
            }
            .withNetwork(network)
            .withNetworkAliases(NETWORK_MAP_ALIAS)
            .withEnv(mapOf(Pair("NMS_STORAGE_TYPE", "file")))
            .waitingFor(Wait.forHttp("").forPort(8080))

        @JvmStatic
        protected fun createNodeContainer(
            name: String,
            location: String,
            country: String,
            p2pPort: Int,
            rpcPort: Int,
            adminPort: Int,
            isNotary: Boolean
        ): KGenericContainer {
            val nodeDir = File(nodes, name).apply { mkdir() }
            createNodeConfFiles(
                name,
                location,
                country,
                p2pPort,
                rpcPort,
                adminPort,
                nodeDir.resolve("node.conf"),
                isNotary
            )
            getCertificate(nodeDir)
            val node = KGenericContainer(CORDA_ZULU_IMAGE)
                .withNetwork(network)
                .withExposedPorts(p2pPort, rpcPort, adminPort)
                .withFileSystemBind(PREFIX + nodeDir.absolutePath, "/etc/corda", READ_WRITE)
                .withFileSystemBind(
                    PREFIX + nodeDir.resolve("certificates").absolutePath,
                    "/opt/corda/certificates",
                    READ_WRITE
                )
                .withEnv("NETWORKMAP_URL", NETWORK_MAP_URL)
                .withEnv("DOORMAN_URL", NETWORK_MAP_URL)
                .withEnv("NETWORK_TRUST_PASSWORD", "trustpass")
                .withEnv("MY_PUBLIC_ADDRESS", "http://localhost:$p2pPort")
                .withCommand("config-generator --generic")
                .withStartupTimeout(timeOut)
                .withCreateContainerCmdModifier {
                    it.withHostName(name.toLowerCase())
                    it.withName(name.toLowerCase())
                }

            if (isNotary) {
                node.start()

                val nodeInfo = extractNotaryNodeInfo(node, nodeDir)
                node.stop()

                updateNotaryInNetworkMap(nodeDir.resolve(nodeInfo).absolutePath)
            } else {
                node.withClasspathResourceMapping("cordapps", "/opt/corda/cordapps", READ_WRITE)
            }

            return node
        }

        private fun createNodeConfFiles(
            name: String,
            location: String,
            country: String,
            p2pPort: Int,
            rpcPort: Int,
            adminPort: Int,
            file: File,
            isNotary: Boolean
        ) {
            Mustache.compiler()
                .compile("nodeConf.mustache")
                .execute(
                    hashMapOf(
                        "name" to name,
                        "isNotary" to isNotary,
                        "location" to location,
                        "country" to country,
                        "p2pPort" to p2pPort,
                        "rpcPort" to rpcPort,
                        "adminPort" to adminPort,
                        "networkMapUrl" to NETWORK_MAP_URL
                    ),
                    PrintWriter(OutputStreamWriter(FileOutputStream(file)))
                )
        }

        private fun getCertificate(node: File) {
            val certificateFolder = File(node, "certificates").apply { mkdir() }
            val certificateFile = certificateFolder.resolve("network-root-truststore.jks")
            val networkMapUrl = "http://localhost:${NETWORK_MAP.getMappedPort(8080)}"

            NetworkMap.build(CordaService(networkMapUrl)).apply {
                Files.write(certificateFile.toPath(), networkMap.truststore)
            }
        }

        private fun extractNotaryNodeInfo(notary: KGenericContainer, notaryNode: File): String {
            var nodeInfo = notary.execInContainer("find", ".", "-maxdepth", "1", "-name", "nodeInfo*").stdout
            nodeInfo = nodeInfo.substring(2, nodeInfo.length - 1) // remove the ending newline character

            notary.copyFileFromContainer(
                "/opt/corda/$nodeInfo",
                notaryNode.resolve(nodeInfo).absolutePath
            )
            notary.execInContainer("rm", "network-parameters")
            return nodeInfo
        }

        private fun updateNotaryInNetworkMap(nodeInfoPath: String) {
            val networkMapUrl = "http://localhost:${NETWORK_MAP.getMappedPort(8080)}"
            var networkMapApi = NetworkMap.build(CordaService(networkMapUrl))

            val loginRequest = LoginRequest("sa", "admin")
            val token = networkMapApi.admin.login(loginRequest)

            networkMapApi = NetworkMap.build(CordaService(networkMapUrl), token)
            networkMapApi.admin.notaries.create(NotaryType.NON_VALIDATING, Files.readAllBytes(Paths.get(nodeInfoPath)))
        }

        class KGenericContainer(imageName: String) : GenericContainer<KGenericContainer>(imageName)
    }
}