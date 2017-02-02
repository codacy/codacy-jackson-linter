This checks definition of overlapping key definitions in JSON files where the last definition would override the previous one.

```json
{
  "foo": 1,
  "foo": 42
}

// foo would be defined as 42 and the first value would be ignored
```