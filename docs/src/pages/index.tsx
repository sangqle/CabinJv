import type { ReactNode } from 'react';
import clsx from 'clsx';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import HomepageFeatures from '@site/src/components/HomepageFeatures';
import Heading from '@theme/Heading';
import Link from '@docusaurus/Link';
import CodeBlock from '@theme/CodeBlock';

import styles from './index.module.css';
import Layout from '@theme/Layout';

function HomepageHeader() {
  const { siteConfig } = useDocusaurusContext();
  return (
    <header className={clsx('hero', styles.heroBanner)}>
      <div className="container">
        <div className={styles.heroContent}>
          <div className={styles.heroText}>
            <Heading as="h1" className="hero__title">
              {siteConfig.title}
            </Heading>
            <p className={styles.heroTagline}>{siteConfig.tagline}</p>
            <p className={styles.heroSubtitle}>
              A lightweight Java framework designed for building efficient,
              maintainable applications with minimal overhead and maximum developer productivity.
            </p>
            <div className={styles.heroButtons}>
              <Link to="/getting-started/intro" className={styles.primaryButton}>
                Get Started
              </Link>
            </div>
          </div>
          <div className={styles.heroGraphic}>
            <img src="/img/banner.jpg" alt="CabinJ Framework" />
          </div>
        </div>
      </div>
    </header>
  );
}

export default function Home(): ReactNode {
  const { siteConfig } = useDocusaurusContext();
  return (
    <Layout
      title={siteConfig.title}
      description="A lightweight Java web framework designed for simplicity and performance">
      <HomepageHeader />
      <main>
        <HomepageFeatures />
        <CodeShowcase />
        {/* <Testimonials /> */}
        <CallToAction />
      </main>
    </Layout>
  );
}

// Simple code showcase section
function CodeShowcase() {
  return (
    <section className={styles.codeShowcase}>
      <div className="container">
        <div className="row">
          <div className="col col--6">
            <Heading as="h2" className={styles.showcaseTitle}>Simple, Yet Powerful API</Heading>
            <p className={styles.showcaseDescription}>Create robust web applications with just a few lines of code. CabinJ's intuitive API lets you focus on building features rather than boilerplate.</p>
          </div>
          <div className="col col--6">
            <div className={styles.codeBlockWrapper}>
              <CodeBlock
                language="java"
                title="Basic Server Example"
                showLineNumbers
              >
{`// Initialize the server
CabinServer server = new ServerBuilder()
    .setPort(8080)
    .build();

// Define a route
server.get("/hello", (req, res) -> {
    res.send("Hello, World!");
});

// Start the server
server.start();`}
              </CodeBlock>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

// Add testimonials section
function Testimonials() {
  return (
    <section className={styles.testimonials}>
      <div className="container">
        <Heading as="h2" className={styles.sectionTitle}>What Developers Say</Heading>
        <div className="row">
          <div className="col col--4">
            <div className={styles.testimonialCard}>
              <p>"CabinJ has dramatically simplified our backend architecture while improving performance by 40%."</p>
              <div className={styles.testimonialAuthor}>
                <strong>Sang Le</strong>
                <span>Senior Software Engineer</span>
              </div>
            </div>
          </div>
          <div className="col col--4">
            <div className={styles.testimonialCard}>
              <p>"The intuitive API and excellent documentation made our team productive from day one."</p>
              <div className={styles.testimonialAuthor}>
                <strong>PhatNT</strong>
                <span>Full-Stack Engineer</span>
              </div>
            </div>
          </div>
          <div className="col col--4">
            <div className={styles.testimonialCard}>
              <p>"We migrated from a heavier framework and cut our response times in half. Highly recommended!"</p>
              <div className={styles.testimonialAuthor}>
                <strong>Jetty Ho</strong>
                <span>Lead Engineer</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

// Add call to action
function CallToAction() {
  return (
    <section className={styles.callToAction}>
      <div className="container">
        <Heading as="h2">Ready to Build with CabinJ?</Heading>
        <p>Join the growing community of developers building high-performance Java applications.</p>
        <Link to="/getting-started/intro" className={styles.primaryButton}>
          Start Building Today
        </Link>
      </div>
    </section>
  );
}