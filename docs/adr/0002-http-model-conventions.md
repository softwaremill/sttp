# 2. HTTP model conventions

Date: 2019-10-24

## Context

The classes which form the HTTP model (in the `sttp.model` package) vary in the conventions they use for error-handling
and the parsing interface they offer.

## Decision

We want to unify the behavior of the HTTP model classes so that they work in a predictable and similar way.
The conventions are:

* `.toString` returns a representation of the model class in a format as in an HTTP request/response. For example,
  for an uri this will be `http://...`, for a header `[name]: [value]`, etc.
* constructors of the model classes are private; instances should be created through methods on the companion objects.
* `[SthCompanionObject].parse(serialized: String): Either[String, Sth]`: returns an error message or an instance of
  the model class
* `[SthCompanionObject].apply(values)`: creates an instance of the model class; validates the input values and in case
  of an error, *throws an exception*. An error could be e.g. that the input values contain characters outside of
  the allowed range
* `[SthCompanionObject].validated(...): Either[String, Sth]`: same as above, but doesn't throw exceptions. Instead,
  returns an error message or the model class instance
* `[SthCompanionObject].notValidated(...): Sth`: creates the model type, without validation, and without throwing
  exceptions 
