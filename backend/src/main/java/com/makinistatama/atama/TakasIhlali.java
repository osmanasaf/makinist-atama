package com.makinistatama.atama;

/** Takas kural kontrolünde bir ihlal ve hangi makinistin olduğu; yalnız atamacıya gösterilir (makinist yanıtlarında ayrıntı YOK [V12]). */
public record TakasIhlali(String sicil, IhlalDto ihlal) {
}
