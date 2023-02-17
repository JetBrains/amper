1. Do we have to keep an empty file for simple builds? What does it mean? Should it be kotlin/jvm or
   kotlin/multiplatform? with what platforms by default?
2. What number of language features such as string interpolation, key reference, lists concatenation do we need to be
   able to conveniently describe an entire project?
3. Do we need inheritance? If yes, what type? Should we allow multiple inheritance?
4. Do we need to add type of module and infer defaults using it?
5. Where should I write a new template/parent?
6. What boundaries of this model? Is packaging in zone of responsibility? deployment?
7. If we have inheritance, how tooling could help with identifying what does the final effective model look like?
8. How obviously setup kotlin settings on different levels? (common, platform-level)
9. Should test binary be just binary artifact or tests should be configured in some other way?
10. Is reference to toolchain useful?
11. Is there any help from splitting workspace and module configs? Maybe it's better to introduce project entity?
12. Should we have the plugin system? What kind of plugins should we support?
13. Do we have some restrictions which are applied by IDE support? which IDE to focus first? fleet? Intellij?
14. Arbitrary key naming could be potentially error-prone
15. A tree-based way to define platforms may be not very useful because we need to duplicate code in leafs
16. How could we identify the root of the project?
17. Do we need syntax sugar on the first MVP?
18. Do we need splitting source and binary dependencies?
19. Should we consider auto-discoverability of modules in FS as performance scalability related restriction?
20. What should be in lock file? Only dependencies? Dependencies + Toolchain? Something else?
21. How to deal with managed/unmanaged dependencies
22. How to generate resources/projects(xcode,android)/code?
23. How should DX be implemented when you want to configure an entire project using only a couple lines of code?
24. Is it better not to provide a way to store credentials in repository from out of box?