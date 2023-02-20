1. Should we choose library platforms from CLI arguments or should we choose platforms
   automatically for case "multiplatform library", when library should support all platforms?
   Like `build --targets 1,2,3` (supporting only 1,2,3 platforms then), or just `build`.

2. Can/should one module have several artifacts, or one artifact per module?
   - one module usually have at least 2 artifacts: production and tests
   - when one module have several artifcats, user will need to specify artifact-specific
     dependencies somehow (e.g. for tests). It might complicates the configuration