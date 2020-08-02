This library provides tools to create a code repository from a
hardcoded enum with just a single annotation.

**'What's a code repository'** you ask me?

Imagine you have a dozen of information systems
containing a set of entities called `Document`.
Each of these systems has a unique identifier for each
entity in this and you end up with some code like this:
```java
enum Document {
    PASSPORT(1L, "PSPRT", 0),
    DRIVER_LICENSE(2L, "DRVR", 12);

    private Long firstSystemCode;
    private String secondSystemCode;
    private Integer thirdSystemCode;

    Document(Long first, String second, Integer third) {
        firstSystemCode = first;
        secondSystemCode = second;
        thirdSystemCode = third;
    }

    public Long getFirstSystemCode;
    public String getSecondSystemCode;
    public Integer getThirdSystemCode;
}
```
After a while you face the task when one of the systems query you
for some data using its own identifier, and you have to query this
data from another system. This requires some kind of repository
which hold all entries and allows fast searches. Something like
```java
class DocumentRepository {
    Document findByFirstSystemCode(Long code);
    Document findBySecondSystemCode(String code);
    Document findByThirsSystemCode(Integer code);
}
```
This task is simple and straightforward when you have juts a couple of
sets like this, but quickly becomes daunting and tedious when the
number of sets starts to grow.

This library allows generating of repositories with just a single
annotation:
```java
@codes.fendever.CorrelationRepositorySource
enum Document
//... the rest is same
```
This code will create a repository using the annotation processing
capabilities. The annotation processor will look into the processed
class in order to obtain getters in order to make lookup funcitons.
These lookup functions will search in the indexes created by the
means of a static `values` function inside annotated class. Actually
any class will do the trick if it has `values` function and required
getters.

