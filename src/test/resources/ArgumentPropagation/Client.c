
// -----------------------------------------------------------------------------
// Implementation of the primitive Client.
// -----------------------------------------------------------------------------

int main(int argc, char *argv[]) {

	int data0 = CALL(dataAccess0, get)();
	int data1000 = CALL(dataAccess1000, get)();

	if (data0 != ATTR(expectedValue0))
		return 1;

	/**
	 * Check critical error:
	 * When a composite with arguments was instanciated more than once, all the argument of the other instances
	 * would get the same arguments value as the first.
	 *
	 * Explanation:
	 * Old flatten algo replaced the argument key-value node in the definition by only
	 * the value, losing "key" info when meeting the first one. All others instances arguments could thus
	 * not be resolved (since no "key" was available anymore).
	 */
	if (data0 == data1000)
		return 2;

	if (data1000 != ATTR(expectedValue1))
		return 3;

	// Everything is ok
	return 0;
}
