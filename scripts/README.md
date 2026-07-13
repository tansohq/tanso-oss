### Tenant Creation SQL Template

This folder contains a SQL template to manually create a new tenant (Account and User) in the database.

#### Files
- `tenant-template.sql`: The SQL script with placeholders.

#### How to use the template

1.  **Open `tenant-template.sql`** and copy the content.
2.  **Generate UUIDs**: You can use `uuidgen` in your terminal or an online generator to replace `{{ACCOUNT_ID}}`, `{{USER_ID}}`, `{{USERS_ACCOUNTS_ID}}`, and `{{API_KEY_ID}}`.
3.  **Generate BCrypt Hash**: The password must be stored as a BCrypt hash with a cost factor of 12.
    - If you have `openssl` or `php` or `python` with `bcrypt`, you can generate it locally.
    - Example (PHP): `php -r "echo password_hash('your_password', PASSWORD_BCRYPT, ['cost' => 12]);"`
4.  **Format API Key**:
    - Non-production: `sk_test_<uuid_without_dashes>`
    - Production: `sk_prod_<uuid_without_dashes>`
5.  **Run the script**: Execute the finalized SQL script against your PostgreSQL database.
