package com.teamdogs.dogs.auth

object AuthValidation {
    fun email(value: String): Boolean = value.trim().let {
        it.length <= 254 && Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(it)
    }

    fun password(value: String): Boolean = value.length in 8..128 &&
        value.any { it in '0'..'9' } && value.any { !it.isLetterOrDigit() && !it.isWhitespace() } &&
        value.none { it.isWhitespace() }

    fun registrationError(name: String, email: String, password: String, confirmation: String): String? = when {
        name.trim().length !in 1..20 -> "이름은 1~20자로 입력해 주세요."
        !email(email) -> "이메일 주소를 확인해 주세요."
        !password(password) -> "비밀번호는 공백 없이 8~128자, 숫자와 특수문자를 포함해 주세요."
        password != confirmation -> "비밀번호가 서로 달라요."
        else -> null
    }
}
