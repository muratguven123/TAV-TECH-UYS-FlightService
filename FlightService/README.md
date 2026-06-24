
## AOP Audit — Self-Invocation Tuzağı

Spring AOP, gerçek nesneleri değil proxy'leri enstrümanlar. `@Auditable` anotasyonu
taşıyan bir metodu **aynı sınıf içinden** `this.metodAdı()` şeklinde çağırdığınızda
çağrı proxy'yi atlar ve `AuditAspect` **hiçbir zaman devreye girmez**.

**Kural:** `@Auditable` metodlar yalnızca harici bean'lerden (controller, başka bir
`@Service`) çağrılmalıdır. `FlightService.create/update/delete` metodlarını doğrudan
`FlightController` çağırdığı için mevcut akışta bu tuzak geçerli değildir.

**Yanlış (aspect çalışmaz):**
```java
// FlightService içinde
public void someHelper() {
    this.create(request);   // ← proxy bypass, audit kaydı oluşmaz
}
```

**Doğru:**
```java
// Controller veya başka bir bean
flightService.create(request);  // ← Spring proxy üzerinden, aspect çalışır
```

Self-invocation'ı zorunlu kılan bir senaryo çıkarsa `@Scope(proxyMode = TARGET_CLASS)`
ile servis bean'i kendi kendini inject edebilir; ancak bu tasarım kötü bir işaret olduğu
için önce mimarinin gözden geçirilmesi önerilir.
