package com.duc.crqa.bug.dto;

import com.duc.crqa.common.enums.CommentType;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CommentForm {

    @NotBlank(message = "Comment cannot be blank")
    private String comment;

    private CommentType commentType = CommentType.COMMENT;
}
