# Trusted Issuers List

The Trusted-Issuers-List Service provides an 
[EBSI Trusted Issuers Registry](https://api-pilot.ebsi.eu/docs/apis/trusted-issuers-registry/v4#/) implementation to act
as the Trusted-List-Service in the DSBA Trust and IAM Framework. 
In addition, a [Trusted Issuers List API](./api/trusted-issuers-list.yaml) to manage the issuers is provided.

## Background

In an DSBA-compliant framework, the [Verifier](https://github.com/FIWARE/VCVerifier) has to check for incoming
[VerifiableCredentials](https://www.w3.org/TR/vc-data-model/) that the corresponding issuer is allowed to issue:
- the given type of credential
- with the given claims
- and at the current time

To do so, it requires a service that provides such information, e.g. the Trusted Issuers List. See the following diagram 
on how the Trusted Issuers List integrates into the framework.

![overview-setup](doc/overview.svg)

