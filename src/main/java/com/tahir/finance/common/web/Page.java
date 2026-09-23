package com.tahir.finance.common.web;

import java.util.List;

/** Keyset page. {@code nextCursor} is null when the caller has reached the end. */
public record Page<T>(List<T> items, String nextCursor) {
}
