/**
* Auth API
* Auth API<br> <a href='/changelog'>Changelog</a>
*
* The version of the OpenAPI document: v0.0.50
* 
*
* NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
* https://openapi-generator.tech
* Do not edit the class manually.
*/
package com.mycelium.bequant.remote.client.models



import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 
 * @param code Phone number confirmation code
 */
data class KycCheckMobilePhoneRequest (
    @JsonProperty("code")
    val code: kotlin.Int
)

