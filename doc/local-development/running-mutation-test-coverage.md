# Running Mutation Testing locally

This library uses [Pitest](http://pitest.org/) to run mutation testing against the codebase in the hopes of increasing the quality of the unit
tests in the application.

## Steps

1. Run the following command `mvn clean install org.pitest:pitest-maven:mutationCoverage`
1. Find the coverage report in each of the modules at `{module}/target/pitest`.
