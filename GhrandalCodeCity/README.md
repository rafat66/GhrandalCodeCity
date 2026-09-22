# GhrandalCodeCity

GhrandalCodeCity is a standalone Java/Swing 3D software-city visualization tool for visualizing object-oriented source code.

## Run in Eclipse

1. Import the `GhrandalCodeCity` directory as an existing Java project.
2. Configure a Java 17 (or compatible) JRE.
3. Open `launch/GhrandalCodeCity.launch` and run it as a **Java Application**.
4. Alternatively, run `Ghrandal.codecity.CodeCityApp` directly.

The project is **not an Eclipse plug-in** and does not require PDE, Equinox, `IApplication`, or `IApplicationContext`.

## Main class

`Ghrandal.codecity.CodeCityApp`

## Software City Structural Representation

| Element | Representation |
|---|---|
| Root district | Entire software city (light brown) |
| Package | District |
| Package nesting | Nested districts with gray shades |
| Classes | Buildings |
| Package containment | All packages within the root district |

The synthetic root district contains every top-level package district. Package paths are represented as nested districts, with classes rendered as buildings inside their package districts.

## Architecture menu

The Architecture menu contains only:

- Metrics Dashboard…
- Dependency Graph…

The Metrics Dashboard provides an **Export Report as HTML** tool with the default filename **Software metrics.html**.
