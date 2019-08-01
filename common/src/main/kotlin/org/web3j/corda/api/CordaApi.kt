package org.web3j.corda.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.web3j.corda.CorDappId
import org.web3j.corda.FlowId
import org.web3j.corda.Party
import org.web3j.corda.SimpleNodeInfo
import org.web3j.corda.validation.HostAndPort
import org.web3j.corda.validation.X500Name
import javax.ws.rs.*
import javax.ws.rs.core.MediaType

@Path("api/rest")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface CordaApi {

    @GET
    @Path("cordapps")
    fun getAllCorDapps(): List<CorDappId>

    @Path("cordapps/{corDappId}")
    fun getCorDappById(corDappId: CorDappId): CorDappResource

    @Path("network")
    fun getNetwork(): NetworkResource
}

interface CorDappResource {

    @GET
    @Path("flows")
    fun getAllFlows(): List<FlowId>

    @Path("flows/{flowId}")
    fun getFlowById(@PathParam("flowId") id: FlowId): FlowResource
}

interface NetworkResource {

    /**
     * Retrieves all nodes.
     */
    @GET
    @Path("nodes")
    fun getAllNodes(): List<SimpleNodeInfo>

    /**
     * Retrieves by the supplied host and port.
     *
     * @param hostAndPort `host:port` for the Corda P2P of the node
     */
    @GET
    @Path("nodes")
    fun getNodesByHostAndPort(@QueryParam("hostAndPort") @HostAndPort hostAndPort: String): List<SimpleNodeInfo>

    /**
     * Retrieves by the supplied X500 name.
     *
     * @param x500Name `host:port` for the Corda P2P of the node
     */
    @GET
    @Path("nodes")
    fun getNodesByX500Name(@QueryParam("x500Name") @X500Name x500Name: String): List<SimpleNodeInfo>

    @GET
    @Path("my-node-info")
    fun getMyNodeInfo(): SimpleNodeInfo

    @GET
    @Path("notaries")
    fun getAllNotaries(): List<Party>
}

interface FlowResource {

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    fun start(vararg parameters: Any): Any
}

fun Any.toJson(): String = jacksonObjectMapper()
    .writerWithDefaultPrettyPrinter()
    .writeValueAsString(this)