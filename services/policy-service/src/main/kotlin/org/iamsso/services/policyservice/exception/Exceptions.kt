package org.iamsso.services.policyservice.exception

open class ServiceException(val code: String, override val message: String) : RuntimeException(message)

class RoleNotFoundException(name: String) : ServiceException("ROLE_NOT_FOUND", "Роль не найдена: $name")
class RoleAlreadyExistsException(name: String) : ServiceException("ROLE_ALREADY_EXISTS", "Роль '$name' уже существует")
class RoleInUseException(name: String) : ServiceException("ROLE_IN_USE", "Роль '$name' используется в политиках и не может быть удалена")
class PolicyNotFoundException(id: Any) : ServiceException("POLICY_NOT_FOUND", "Политика не найдена: $id")
class PolicyAlreadyExistsException(name: String) : ServiceException("POLICY_ALREADY_EXISTS", "Политика '$name' уже существует")
