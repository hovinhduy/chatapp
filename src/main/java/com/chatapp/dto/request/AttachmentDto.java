/*
 * @ (#) AttachmentDto.java        1.0     4/17/2025
 *
 * Copyright (c) 2025 IUH. All rights reserved.
 */

package com.chatapp.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
 * @description
 * @author: Ho Vinh Duy
 * @date:   4/17/2025
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
class AttachmentDto {
    private Long id;
    private String name;
    private String type;
    private String url;
    private double size;
}