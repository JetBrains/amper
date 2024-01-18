### TODO

- [x] Dependency cache
- [ ] Progress
- [x] Real JSON/XML files parsing tests
- [x] Error handling/propagation
- [x] Support different artifact types (aar, pom, etc.)
- [x] Conflict resolution (move #creteOrReuse out of MavenDep)
- [ ] Suspend getChildren() in case of version conflict
- [ ] Coroutines scope per MavenDependency
- [ ] Dump resolution graph
- [ ] Write tests for partial resolution
- [ ] CacheDirectoryComposite
- [ ] Support fragments: common(d1) ios(d2) android(d3)
- [ ] Order of dependencies
- [ ] Resolve: from root or per module (Go modules, Run task)?
- [ ] Separate IO dispatcher