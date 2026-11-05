package com.shipflow.tasks.reviews;
import com.shipflow.tasks.Task; import com.shipflow.users.User; import jakarta.persistence.*; import java.time.Instant;
@Entity @Table(name="task_reviews")
public class TaskReview {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="task_id",nullable=false) private Task task;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="requested_by",nullable=false) private User requestedBy;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="reviewed_by") private User reviewedBy;
 @Column(nullable=false,length=30) private String status="PENDING";
 @Column(columnDefinition="TEXT") private String comment;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="reviewed_at") private Instant reviewedAt;
 protected TaskReview() {}
 public TaskReview(Task task,User requester){this.task=task;this.requestedBy=requester;}
 @PrePersist void created(){if(createdAt==null)createdAt=Instant.now();}
 public void resolve(User reviewer,String status,String comment){this.reviewedBy=reviewer;this.status=status;this.comment=comment;this.reviewedAt=Instant.now();}
 public Long getId(){return id;} public Task getTask(){return task;} public User getRequestedBy(){return requestedBy;} public User getReviewedBy(){return reviewedBy;} public String getStatus(){return status;} public String getComment(){return comment;} public Instant getCreatedAt(){return createdAt;} public Instant getReviewedAt(){return reviewedAt;}
}
