web3j integration for Corda
===========================

## Introduction

web3j-corda is a lightweight client library for working with CorDapps and interacting with different nodes on Corda network.

![web3j-corda Network](docs/img/web3j-corda.png)

## Features
* Connect to a Corda node.
* Query the available CorDapps in the node.
* Generate CorDapp wrappers to interact with the deployed CorDapps
* Generate automated tests using Docker containers to verify the working of CorDapp. 
* Validate client-side of Corda API requests.


## Quickstart

A [web3j-corda sample project](https://gitlab.com/web3j/corda-samples) is available that demonstrates a number of core features of Cordapp using web3j-corda, including:
* Interact with a CorDapp
* Generate automated tests using Docker containers to verify the working of CorDapp. 

## Getting started

Add the relevant dependency to your project:

### Maven

```xml
<dependency>
    <groupId>org.web3j</groupId>
    <artifactId>web3j-corda</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Gradle

```groovy
dependencies {
    implementation 'org.web3j:web3j-corda:0.1.0-SNAPSHOT'
}
```

## Connect to a Corda Node

To print all the nodes connected to the current node:

```kotlin
val service = CordaService("http://localhost:9000/") // URL exposed by BRAID service
val corda = Corda.build(service)
corda.network.nodes.findAll()
```

To query the list of all running CorDapps:

```kotlin
val service = CordaService("http://localhost:9000/") // URL exposed by BRAID service
val corda = Corda.build(service)
corda.corDapps.findAll()
```

To start a flow there are two option depending on whether you want to use a generated CorDapp wrapper
or just the Corda API directly:

### Using Corda API
This way works but is not type-safe, so can lead to runtime exceptions:
```kotlin
// Initialise the parameters of the flow 
val params = InitiatorParameters("$1", "O=PartyA, L=London, C=GB", false)

// Response can be Any
val signedTxAny = corda
    .corDapps.findById("obligation-cordapp")
    .flows.findById("issue-obligation")
    .start(parameters) // Potential runtime exception!

// Type-conversion with potential runtime exception!
var signedTx = convert<SignedTransaction>(signedTxAny)
```

### Using the web3j CorDapp wrapper
By using a wrapper generated by the `web3j-corda` Command-Line Tool, 
you can interact with your CorDapp in a type-safe way:
```kotlin
// Initialise the parameters of the flow 
val params = InitiatorParameters("$1", "O=PartyA, L=London, C=GB", false)

// Start the flow with typed parameters and response
val signedTx = Obligation.load(corda).flows.issue.start(parameters)
```
