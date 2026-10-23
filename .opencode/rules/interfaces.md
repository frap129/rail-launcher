---
globs:
  - '**/*.kt'
---

# Interfaces

- Consume every collaborator through an interface. A collaborator is a class other code depends on (injected through Koin or passed to a constructor) that has side effects or framework access.
- Name the interface `IFoo` and its implementation `IFooImpl`. Never `AndroidFoo`, `FooImpl` or other variants.
- Bind the interface in Koin (`single<IFoo> { IFooImpl(...) }`) and declare the interface type in every consumer.
- Inject collaborators; never construct one inside a ViewModel or another collaborator.
- When adding a new collaborator, add its interface in the same change.
- Exempt: Room `@Dao` interfaces, sealed interfaces used as sum types (`WidgetModel`, `HomeEditMode`), and pure state holders, formatters and event relays (`HomeEditState`, `HomeHeightState`, `HeaderFormatter`).
