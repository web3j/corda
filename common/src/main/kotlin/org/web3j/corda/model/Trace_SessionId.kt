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
package org.web3j.corda.model

/**
 *
 * @param value
 * @param timestamp JSR310 encoded time representation of Instant
 * @param entityType
 */
data class Trace_SessionId(
    val value: kotlin.String,
    /* JSR310 encoded time representation of Instant */
    val timestamp: kotlin.String,
    val entityType: kotlin.String? = null
)
