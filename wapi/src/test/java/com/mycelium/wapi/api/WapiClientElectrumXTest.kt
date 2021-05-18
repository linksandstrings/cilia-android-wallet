package com.mycelium.wapi.api

import com.mycelium.net.ServerEndpoints
import com.mycelium.wapi.api.jsonrpc.RpcResponse
import com.mycelium.wapi.api.jsonrpc.TcpEndpoint
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before
import org.mockito.Mockito.mock

class WapiClientElectrumXTest {
    private val version = "testing"
    var sut: WapiClientElectrumX? = null

    @Before
    fun setup() {
        val tcpEndpoints = arrayOf(TcpEndpoint("localhost", 30000))
        val bitcoinILtcpEndpoints = arrayOf(TcpEndpoint("localhost", 30000))
        val wapiEndpoints: ServerEndpoints = mock(ServerEndpoints::class.java) as ServerEndpoints
//        sut = WapiClientElectrumX(wapiEndpoints, tcpEndpoints, version, 29)
        sut = WapiClientElectrumX(wapiEndpoints, tcpEndpoints, bitcoinILtcpEndpoints, version, 29)

    }

    @Test
    fun handleBroadcastResponseMissingInputs() {
        val expectedWapiErrorCode = 101
        val json = """{"jsonrpc": "2.0", "error": {"code": "1", "message": "the transaction was rejected by network rules.\n\nMissing inputs\n[0100000000010154d6f5d93da6f3f50e3434228f06615052e437e59d1fe52c6c88e7c681ef15ed0000000000ffffffff02566815000000000017a91477167673f495cf6c9da0544bcd9d01385317dcaa8744da83000000000017a91495ba2ecf38c4cd21020af7e5e01bb0d3861dcb508702473044022032c62e828274505c11f1d2b8d897d32cae3de37c6b5533eb58f2b647a043710802205095ae8678ad6d12b70631576d36aeebbbdb6695219ba210ec26f7f7b37afedf01210340f2954eee63e36ac0d170e7260392942282f2c03de4229d1bfb4c1ad4f1ce6700000000]"}, "id": "574"}""";
        // The json is from an actual crash, with actual error.code being 1.
        val response = listOf(RpcResponse.fromJson(json))
        val result = sut!!.handleBroadcastResponse(response)
        assertEquals("The errorcode should be copied from the json", expectedWapiErrorCode, result.errorCode)
    }

    @Test
    fun handleUnexpectedBroadcastErrorFromElectrumX() {
        val expectedWapiErrorCode = 6000
        val json = """{"jsonrpc": "2.0", "error": {"code": "907871", "message": "the transaction was rejected by network rules.\n\nMissing inputs\n[0100000000010154d6f5d93da6f3f50e3434228f06615052e437e59d1fe52c6c88e7c681ef15ed0000000000ffffffff02566815000000000017a91477167673f495cf6c9da0544bcd9d01385317dcaa8744da83000000000017a91495ba2ecf38c4cd21020af7e5e01bb0d3861dcb508702473044022032c62e828274505c11f1d2b8d897d32cae3de37c6b5533eb58f2b647a043710802205095ae8678ad6d12b70631576d36aeebbbdb6695219ba210ec26f7f7b37afedf01210340f2954eee63e36ac0d170e7260392942282f2c03de4229d1bfb4c1ad4f1ce6700000000]"}, "id": "574"}""";
        // The json is from an actual crash, with actual error.code being 1.
        val response = listOf(RpcResponse.fromJson(json))
        val result = sut!!.handleBroadcastResponse(response)
        assertEquals("Unable to correctly handle unknown ElectrumX error", expectedWapiErrorCode, result.errorCode)
    }
}