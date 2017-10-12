# hot-knife

Hot knife FIB-SEM slab series stitching pipeline.

## Transformations

Transformations are stored as scaled inverse coordinate lookup tables at double (float64) precision.
Memory layout is row-major. The fastest running dimension is the dimension index, i.e. a 2-dimensional
lookup field for a 200px(width) * 100px(height) image is a tensor of 200 * 100 * 2 elements
([2, 100, 200] for NumPy or HDF5).
**TODO:** Since coordinate lookups are typically done for all dimensions simultaneously, this layout
is not CPU cache efficient and should be replaced by dimension index being the slowest running
dimension.

In N5, vector attributes are stored as column-major arrays which is nice for ImgLib2 and Vigra.  In HDF5,
vector attributes are stored as row-major arrays which is nice for NumPy.  **TODO:** This is stupid
because the lookup table itself is always column-major, i.e. the x-coordinate lookup is the first slice,
the y-coordinate lookup is the second slice, ... Turning the vectors around in HDF5 does not help but
adds unnecessary confusion.

Each transformation has the attributes:

* `boundsMin` : double precision vector, top left corner of bounding box in unscaled world coordinates,
* `boundsMax` : double precision vector, bottom right corner of bounding box in unscaled world coordinates,
* `scale` : double precision scalar, scale of the transformation (0.5 means the transformation field is half the size of the bounding box).

`boundsMin` and `scale` define an integer offset of the lookup field:

```python
offset[i] = floor(boundsMin[i] * scale)
```
To make a world coordinate lookup, slice the ''n''+1-dimensional lookup table into ''n'' single dimension lookup tables, border extend them
and interpolate (I use ''n''-linear interpolation, but other interpolations are legal).

