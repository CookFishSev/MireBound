# Stain rendering ownership

Local checkpoint before this cleanup: `4c3d80a`, tag
`checkpoint/stains-before-cleanup-20260912-234911`.
The checkpoint includes the unsuccessful first ITRP fix. It is not a verified
shader-compatible release. Beta1.2.2 remains available separately.

## Findings and changes

- Wall imprints used a fused texture, but flow previews were separate coplanar
  ribbon quads using another texture and render type. They did not share model
  clipping, and their fade used the footprint lifetime instead of the wall
  lifetime. The previews now rasterize into the same wall texture and use the
  same clipping, color sampling and pixel lifetime. There is no flow mesh.
- An unused rectangle-creation API still had a separate renderer with fringe
  quads. The API and renderer are removed. Old saved rectangles convert to wall
  pixels once on load, preserving their identity, material, age and fade.
- Corner wrapping dropped secondary material data. Wrapped pixels now retain
  both materials, their blend weight and creation time. All six source faces
  and four edges are covered by coordinate tests.
- The client used to rebuild the 256-cell model visibility mask every tick.
  The mask now survives until its geometry identity changes. Voxel fallback
  and server contact share `WallStainGeometry` for support clipping.
- Preview growth stops at shape holes and the owning face boundary. It does
  not draw unsupported strips over another block or across an inner corner.
  Actual adjacent-face stains remain server-owned through
  `WallStainCornerWrap` and the existing exposed-corner checks.

## Owners and budgets

- `MudWallStainSystem`: contact transfer, source depletion and supported targets.
- `MudFootprintBlockEntity`: persistent pixels, washing, lifetime and ceiling
  drip loss. Its server flow method handles ceiling drips, not wall previews.
- `WallStainGeometry`: collision-face support queries shared by server/client.
- `WallStainCornerWrap`: local-coordinate transfer across exposed outer edges.
- `MudWallFlowLayout`: deterministic selection and bounded face-local raster.
- `MudWallTextureCache`: one composition, with disjoint stable/fading pixels;
  at most four preview channels per face across all materials, reusable scratch
  arrays, and the existing limit of 24 texture rebuilds per game tick.
- `MudFootprintBlockEntityRenderer`: final surface geometry and hanging strands.
  Flat stains do not cast duplicate shadows; hanging geometry retains shadows.

## Scope and verification

Skin's canonical contact topology, animated UV capture and equipment's optional
surface-local path have distinct responsibilities. The classic equipment path
is explicitly retained as a user option; render entry points gate the selected
path. They are not merged as part of this wall-stain cleanup.

The available initial Mirebound source release already contained wall imprints,
legacy rectangles and flow ribbons. It does not establish which earlier
prototype was visually correct with ITRP.

Automated checks cover raster bounds, holes, local gravity, growth, legacy
conversion, material retention and edge coordinates. Shader appearance is
accepted by the user in the independent test instance; unit tests cannot prove
that the reported ITRP artifact is gone.

## ITRP face-corner investigation (2026-09-13)

The cleanup above did not remove the reported corner depressions. An isolated
OpenGL probe subsequently reproduced the problem using the installed ITRP
0.8.26 hotfix's own `BilinearHeightSample` function with `PARALLAX_MODE=3`.
The function was read from the user's local pack for the probe, not copied into
the mod or distributed here.

Iris has no built-in PBR loader for DynamicTexture. Its neutral normal/height
fallback is 1x1, while the wall albedo is 16x16. ITRP uses albedo texel coordinates
for neighboring integer height fetches, then averages before treating zero
height as absent. On the NVIDIA RTX 4080 Laptop GPU, the probe returned height
64/255 at (0,0), (15,0), (0,15), (15,15) with the 1x1 fallback. A 16x16 neutral
height texture returned full height everywhere, with no parallax candidates.

`client/compat/IrisDecalMaterials` registers an optional PBR loader only for our
owned decal texture subclass. It supplies albedo-sized flat normal/height and
matte specular maps, including the transparent albedo pixels. The maps contain
no simulated relief. Wall and footprint textures use this factory; other mods'
textures and shader settings are not modified. Iris owns material-map cleanup
when the base texture is deleted or its PBR cache reloads. No maps are rebuilt
per frame or per opacity change.

## Contour variation and update costs (2026-09-13)

- Wall color composition applies deterministic spatial mottling and trims at
  most two pixels inward at exposed edges. The pattern uses global face-plane
  pixel coordinates, not frame time or entry creation time. It adds no geometry
  or pixels outside authoritative contact. Opaque interiors remain opaque.
- Four loaded coplanar neighbors supply a two-pixel halo so adjacent blocks do
  not acquire artificial seams. Block entities lazily cache occupancy row bits
  and invalidate them on synchronized changes/loading. Reading halo rows does
  not scan each neighbor's pixels again. No chunks are loaded for the halo.
- Fully grown flow uses the rounded curve's constant result after six time
  constants; neighboring pixels with the same age share growth/fade calculations.
- New texture allocation is included in the existing 24-rebuild-per-tick budget.
  Unchanged stable/fading images skip GPU uploads, and render frames reuse the
  cached texture view instead of allocating another wrapper.
- Verification covers seam continuity at positive/negative coordinates,
  monotonic fading, opacity preservation, halo bit boundaries and dirty-image
  updates. These are bounded-cost/code checks, not an in-game FPS benchmark.
