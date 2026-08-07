# Player audit from top balances

## Objective

Give Wallet administrators access to the latest 100 transactions for a player directly from the top-balance table and ensure the world account uses the normal debitable system-account path.

## Ownership, compatibility, and rollback

Wallet remains the sole owner of balances and transaction history. The UI action is additive; existing transfer APIs and history remain unchanged. Roll back the plugin artifact to remove the action.

## Validation

Build and UI-code inspection validate the path; administrator interaction and a world-account debit require Dev acceptance.

## Checklist

- [x] Add an action column to top balances.
- [x] Render selected player's last 100 transactions with a back action.
- [x] Verify the existing system-account debit path, package, and deploy to Dev.
