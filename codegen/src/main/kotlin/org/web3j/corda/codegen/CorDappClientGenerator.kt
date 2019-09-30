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
package org.web3j.corda.codegen

import io.github.classgraph.ClassGraph
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
import io.swagger.v3.parser.core.models.SwaggerParseResult
import org.openapitools.codegen.ClientOptInput
import org.openapitools.codegen.ClientOpts
import org.openapitools.codegen.CodegenConstants.APIS
import org.openapitools.codegen.CodegenConstants.API_TESTS
import org.openapitools.codegen.CodegenConstants.MODELS
import org.openapitools.codegen.CodegenConstants.MODEL_PACKAGE
import org.openapitools.codegen.CodegenConstants.PACKAGE_NAME
import org.openapitools.codegen.DefaultGenerator
import org.openapitools.codegen.config.GeneratorProperties.setProperty
import org.web3j.corda.model.AmountCurrency
import org.web3j.corda.model.Error
import org.web3j.corda.model.core.contracts.Issued
import org.web3j.corda.model.core.contracts.Issued_issuer
import java.io.File

class CorDappClientGenerator(
    private val packageName: String,
    private val openApiDef: String,
    private val outputDir: File,
    private val generateTests: Boolean
) : DefaultGenerator(), CordaGenerator {

    override fun generate(): List<File> {

        // Filter common API endpoints
        val result = parser.readContents(openApiDef, listOf(), parseOptions).apply {
            openAPI.paths.entries.removeIf {
                it.key == "/cordapps" || !it.key.startsWith("/cordapps") || it.key.endsWith("/flows")
            }
        }
        configureTypeMappings()
        opts(
            ClientOptInput()
                .config(CorDappClientCodegen(packageName, outputDir, typeMapping))
                .opts(ClientOpts())
                .openAPI(result.openAPI)
        )
        configureGeneratorProperties(result)
        setGenerateMetadata(false)

        return super.generate().onEach {
            CordaGeneratorUtils.kotlinFormat(it)
        }
    }

    override fun processTemplateToFile(
        templateData: MutableMap<String, Any>,
        templateName: String,
        outputFilename: String
    ): File {
        templateData["package"].toString().let {
            templateData[MODEL_PACKAGE] = it
            templateData[PACKAGE_NAME] = it
        }
        return super.processTemplateToFile(templateData, templateName, outputFilename)
    }

    private fun configureTypeMappings() {

        // Corda types without package
        typeMapping["AmountCurrency"] = AmountCurrency::class.qualifiedName!!
        typeMapping["Issued_issuer"] = Issued_issuer::class.qualifiedName!!
        typeMapping["Issued"] = Issued::class.qualifiedName!!
        typeMapping["Error"] = Error::class.qualifiedName!!

        // Map Corda and Braid model classes to avoid re-generation
        ClassGraph().enableClassInfo().scan().allClasses.apply {

            filter { it.packageName.startsWith(CORDA_REPACKAGED) }.forEach {
                val repackaged = it.packageName.replace(CORDA_REPACKAGED, "net.corda")
                typeMapping["$repackaged.${it.simpleName}"] = it.name
            }

            filter { it.packageName.startsWith(BRAID_REPACKAGED) }.forEach {
                val repackaged = it.packageName.replace(BRAID_REPACKAGED, "io.bluebank.braid.corda")
                typeMapping["$repackaged.${it.simpleName}"] = it.name
            }
        }
    }

    private fun configureGeneratorProperties(result: SwaggerParseResult) {
        val models = result.openAPI.components.schemas.keys.filter {
            !typeMapping.keys.contains(it) && // FIXME This shouldn't be required!
                    !it.startsWith("net.corda.core.utilities.NonEmptySet")
        }

        // Specify the list of model classes to generate
        setProperty(MODELS, models.joinToString(separator = ","))
        setProperty(APIS, result.openAPI.paths.keys.joinToString(separator = ",") {
            buildCorDappNameFromPath(it)
        })
        setProperty(API_TESTS, generateTests.toString())
    }

    companion object {
        /**
         * Corda model classes are taken from web3j pre-generated code.
         */
        private const val CORDA_REPACKAGED = "org.web3j.corda.model"

        /**
         * Braid model classes are taken from web3j pre-generated code.
         */
        private const val BRAID_REPACKAGED = "org.web3j.braid"

        private val parser = OpenAPIV3Parser()
        private val parseOptions = ParseOptions()

        private val typeMapping = mutableMapOf<String, String>()

        fun buildCorDappNameFromPath(path: String): String {
            return (path.split("/".toRegex())[2])
        }
    }
}
