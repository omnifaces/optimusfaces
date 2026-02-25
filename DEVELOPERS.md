# Build

```bash
mvn clean install
```

This by default skips Failsafe plugin (for integration tests).

# Run integration tests

Ensure that `it.sh` is executable:

```bash
chmod +x it.sh
```

Then run it:

```bash
./it.sh
```

It will pass-through all arguments to underlying `mvn clean verify`, so to run a different server profile, just use `-P` usual:

```bash
./it.sh -P glassfish-eclipselink
```

The `it.sh` script by default skips Surefire plugin (for unit tests) as well as Javadoc plugin.
