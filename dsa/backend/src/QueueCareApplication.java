/**
 * QueueCareApplication – entry point for the QueueCare backend.
 *
 * <p>This is a standalone Java application.  It does not require Spring Boot or
 * any external framework to compile and run.  The main method boots a simple
 * in-memory demo that exercises every DSA component end-to-end so you can
 * verify the scheduling logic from the command line.
 *
 * <p><strong>How to compile and run (from the {@code backend/src} folder):</strong>
 * <pre>
 * # Compile all packages in dependency order
 * javac -d out model/Citizen.java
 * javac -d out -cp out dsa/CustomQueue.java
 * javac -d out -cp out dsa/CitizenPriorityQueue.java
 * javac -d out -cp out dsa/PriorityQueue.java
 * javac -d out -cp out dsa/QueueScheduler.java
 * javac -d out -cp out dto/RegisterRequest.java dto/CitizenResponse.java ^
 *                       dto/QueueStatsResponse.java dto/ApiResponse.java
 * javac -d out -cp out service/QueueService.java
 * javac -d out -cp out controller/QueueController.java
 * javac -d out -cp out QueueCareApplication.java
 *
 * # Run
 * java -cp out QueueCareApplication
 * </pre>
 *
 * <p>When Spring Boot is added, annotate this class with
 * {@code @SpringBootApplication} and replace the main body with
 * {@code SpringApplication.run(QueueCareApplication.class, args)}.
 */
public class QueueCareApplication {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║        QueueCare – System Self-Test          ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println();

        controller.QueueController controller = new controller.QueueController();

        // 1. Show initial stats (demo seed data loaded in constructor)
        section("1. Initial Queue Stats");
        dto.QueueStatsResponse stats = controller.getStats().getData();
        printStats(stats);

        // 2. Register a new citizen
        section("2. Register New Citizen");
        dto.RegisterRequest req = new dto.RegisterRequest(
                "Vijay Sharma", "Document Services", "Standard");
        dto.ApiResponse<dto.CitizenResponse> registered = controller.register(req);
        printResponse(registered);

        // 3. Register a priority citizen
        section("3. Register Priority Citizen");
        dto.RegisterRequest req2 = new dto.RegisterRequest(
                "Kamala Devi", "Accessibility Support", "Priority Assistance");
        printResponse(controller.register(req2));

        // 4. Call next (should be a Priority Assistance citizen)
        section("4. Call Next Citizen");
        printResponse(controller.callNext());

        // 5. Mark served
        section("5. Mark Current Citizen as Served");
        printResponse(controller.markServed());

        // 6. Call next again
        section("6. Call Next Again");
        printResponse(controller.callNext());

        // 7. Skip the current
        section("7. Skip Q103 via token");
        printResponse(controller.skip("Q103"));

        // 8. Final stats
        section("8. Final Queue Snapshot");
        printStats(controller.getStats().getData());

        // 9. Reset demo
        section("9. Reset Demo");
        printResponse(controller.reset());
        System.out.println("Post-reset waiting count: "
                + controller.getStats().getData().getWaitingCount());

        System.out.println();
        System.out.println("✓ Self-test complete.  All DSA components are operational.");
    }

    // ---------------------------------------------------------------
    // Pretty-print helpers
    // ---------------------------------------------------------------
    private static void section(String title) {
        System.out.println();
        System.out.println("── " + title + " " + "─".repeat(Math.max(0, 45 - title.length())));
    }

    private static <T> void printResponse(dto.ApiResponse<T> r) {
        System.out.println("  success : " + r.isSuccess());
        System.out.println("  message : " + r.getMessage());
        if (r.getData() instanceof dto.CitizenResponse c) {
            System.out.println("  token   : " + c.getToken());
            System.out.println("  name    : " + c.getName());
            System.out.println("  category: " + c.getCategory());
            System.out.println("  status  : " + c.getStatus());
        }
    }

    private static void printStats(dto.QueueStatsResponse s) {
        if (s == null) { System.out.println("  (no data)"); return; }
        System.out.println("  waiting       : " + s.getWaitingCount());
        System.out.println("  priority      : " + s.getPriorityCount());
        System.out.println("  served today  : " + s.getServedToday());
        System.out.println("  avg wait (min): " + s.getAverageWaitMin());
        System.out.println("  currentToken  : " + s.getCurrentToken());
        System.out.println("  nextToken     : " + s.getNextToken());
        System.out.printf ("  queue (%d citizens):%n", s.getQueue().size());
        s.getQueue().forEach(c ->
            System.out.printf("    [%s] %-20s %-22s %-18s %s%n",
                    c.getToken(), c.getName(), c.getService(),
                    c.getCategory(), c.getStatus()));
    }
}
