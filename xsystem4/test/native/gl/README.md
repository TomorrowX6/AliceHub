# PartsEngine2 GLES integration regression

This is a **synthetic renderer test, not a Dohna Dohna game-scene test**.
No game assets, game scripts or saved games are used.

The test compiles the production translation units (`video.c`, `draw.c`,
`parts.c`, `render.c`, `construction.c`, `PartsEngine.c`) and loads the real
GLES shaders. Test-only wrappers expose file-local entry points; production
rendering functions are not reimplemented or extracted as source text.
Unused VM/audio/game entry points are discarded by the linker.

Required on Linux: C compiler, SDL2 runtime/headers, cglm headers, GLES3 headers,
Mesa EGL/GLES with an offscreen software context. For example:

```sh
SDL2_INCLUDE=/usr/include/SDL2 CGLM_INCLUDE=/usr/include \
  ./xsystem4/test/native/gl/run.sh /tmp/parts-gles-test
```

The harness checks actual GPU readback against independently computed pixels:

* Construction H/V blur, full-size/subrect, pinned/unpinned draw target,
  RGB results, alpha preservation, two-pass blur.
* Normal/LR/TB/combined sprite rendering through the real HLL setters,
  cached world matrix and CG renderer, including exact output position.
* Every actual `glDrawElements` is audited: texture unit 0 must not contain
  the texture attached to the draw framebuffer. The old blur implementation
  fails this invariant, regardless of whether a driver happens to look right.

The only test-path substitutions are component lookup (returns the fixture
component instead of running the VM) and an alpha-clipper construction guard
(aborts if invoked; these fixtures intentionally have no alpha clipper).
Actual component dirtying, transforms, shader compilation, framebuffer changes,
texture copies, blend state and GL draws run unchanged production code.

Generated `*-gles-synthetic.ppm` files are raw GL readbacks of synthetic art.
They are useful diagnostic images, **not proof of the supplied game scenes**.
The plain C fixture/source-contract suite in the parent directory remains
complementary coverage for rotation and save-version handling.
