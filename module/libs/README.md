# libxposed settings bridge (102.0.0)

The only bundled runtime libraries are the official libxposed service bridge and its Binder interface. Audio, UI and S3 have no additional runtime dependencies. `libxposed-api-102.jar` remains compile-only (provided by LSPosed).

Source artifacts (Maven Central):
- https://repo.maven.apache.org/maven2/io/github/libxposed/service/102.0.0/service-102.0.0.aar
- https://repo.maven.apache.org/maven2/io/github/libxposed/interface/102.0.0/interface-102.0.0.aar

Each checked-in jar is the unmodified `classes.jar` from its AAR. No native binaries. Apache-2.0; see `LICENSE-libxposed.txt`. Sources: https://github.com/libxposed/service and corresponding Maven `*-sources.jar` artifacts.

SHA-256:
```
08711eceb0ffb64cbade3536fa7bb48a5fe3420dfdb1e3edde12cea006ae207f  libxposed-interface-102.0.0.jar
b07bc86b9da1e9adc4fd967d5f27a37f1907e8234594a20e6928c78981f0f37f  libxposed-service-102.0.0.jar
```

`build.sh` packages these jars with D8. Settings Application registers `XposedServiceHelper`; the official exported provider receives the framework Binder. The Activity writes `XposedService.getRemotePreferences("voice")`; the module reads `XposedModule.getRemotePreferences("voice")` and retains a change listener. No world-readable files, unauthenticated settings-write provider, or hand-written Binder transaction IDs.
