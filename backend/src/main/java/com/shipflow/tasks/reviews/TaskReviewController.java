package com.shipflow.tasks.reviews;
import com.shipflow.tasks.reviews.dto.*; import jakarta.validation.Valid; import org.springframework.http.HttpStatus; import org.springframework.web.bind.annotation.*; import java.util.List;
@RestController @RequestMapping("/api") public class TaskReviewController {
 private final TaskReviewService service; public TaskReviewController(TaskReviewService service){this.service=service;}
 @PostMapping("/tasks/{taskId}/reviews") @ResponseStatus(HttpStatus.CREATED) public TaskReviewResponse request(@PathVariable Long taskId){return service.request(taskId);}
 @GetMapping("/tasks/{taskId}/reviews") public List<TaskReviewResponse> list(@PathVariable Long taskId){return service.list(taskId);}
 @PatchMapping("/reviews/{reviewId}") public TaskReviewResponse decide(@PathVariable Long reviewId,@Valid @RequestBody ReviewDecisionRequest request){return service.decide(reviewId,request);}
}
